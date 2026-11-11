package test.unit.gov.nist.javax.sip.stack.fallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.Utils;
import gov.nist.javax.sip.message.MessageExt;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Calls over UDP, ACKs through the dialog (or by hand without dialogs), answers the BYE.
 * UDP and TCP on the same port, a UDP message over the limit goes again over TCP.
 */
public class Shootist implements SipListenerExt {

    private static final StackLogger logger = CommonLogger.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int peerPort;
    private final boolean dialogs;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStackImpl sipStack;
    private final SipProviderImpl sipProvider;

    /** text body on the INVITE, 0 for none */
    public int inviteBodySize;

    private final List<String> received = Collections.synchronizedList(new ArrayList<String>());
    private final Set<Long> acked = ConcurrentHashMap.newKeySet();
    private final AtomicInteger fallbacks = new AtomicInteger();
    /** other IOExceptions, timeouts, dialog timeouts */
    private final AtomicInteger errors = new AtomicInteger();
    private final AtomicInteger dialogsTerminated = new AtomicInteger();
    private volatile boolean okSeen;
    private volatile boolean byeSeen;

    public Shootist(int port, int peerPort, boolean dialogs, Properties stackProperties) {
        this.port = port;
        this.peerPort = peerPort;
        this.dialogs = dialogs;
        ProtocolObjects protocolObjects = new ProtocolObjects("fallback-shootist-" + port, "gov.nist",
                ListeningPoint.UDP, dialogs, false, false, stackProperties);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = (SipStackImpl) protocolObjects.sipStack;
        try {
            ListeningPoint udp = sipStack.createListeningPoint(myAddress, port, ListeningPoint.UDP);
            this.sipProvider = (SipProviderImpl) sipStack.createSipProvider(udp);
            this.sipProvider.addListeningPoint(sipStack.createListeningPoint(myAddress, port, ListeningPoint.TCP));
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootist", ex);
        }
    }

    public void sendInvite() throws Exception {
        SipURI requestUri = addressFactory.createSipURI("callee", myAddress);
        requestUri.setPort(peerPort);
        FromHeader from = headerFactory.createFromHeader(addressFactory.createAddress("sip:caller@" + myAddress),
                Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));
        ToHeader to = headerFactory.createToHeader(addressFactory.createAddress("sip:callee@" + myAddress), null);
        List<ViaHeader> vias = new ArrayList<ViaHeader>();
        vias.add(headerFactory.createViaHeader(myAddress, port, ListeningPoint.UDP, null));
        Request invite = messageFactory.createRequest(requestUri, Request.INVITE, sipProvider.getNewCallId(),
                headerFactory.createCSeqHeader(1L, Request.INVITE), from, to, vias,
                headerFactory.createMaxForwardsHeader(70));
        invite.addHeader(createContact());
        if (inviteBodySize > 0) {
            invite.setContent(FallbackTest.body(inviteBodySize), headerFactory.createContentTypeHeader("text", "plain"));
        }
        sipProvider.getNewClientTransaction(invite).sendRequest();
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        if (responseEvent instanceof ResponseEventExt && ((ResponseEventExt) responseEvent).isRetransmission()) {
            return;
        }
        received.add(FallbackTest.describe(response));
        logger.logInfo("shootist:" + port + " received " + FallbackTest.describe(response));
        long cseq = ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber();
        if (response.getStatusCode() != Response.OK || !acked.add(cseq)) {
            return;
        }
        okSeen = true;
        try {
            if (dialogs) {
                Dialog dialog = responseEvent.getDialog();
                dialog.sendAck(dialog.createAck(cseq));
            } else {
                sendAck(response, cseq);
            }
        } catch (Exception ex) {
            logger.logError("shootist: could not ACK", ex);
        }
    }

    /** no dialog: ACK to the Contact, From/To/Call-ID from the 200, new branch, sent statelessly */
    private void sendAck(Response ok, long cseq) throws Exception {
        List<ViaHeader> vias = new ArrayList<ViaHeader>();
        vias.add(headerFactory.createViaHeader(myAddress, port, ListeningPoint.UDP,
                Utils.getInstance().generateBranchId()));
        Request ack = messageFactory.createRequest(((ContactHeader) ok.getHeader(ContactHeader.NAME)).getAddress().getURI(),
                Request.ACK, (CallIdHeader) ok.getHeader(CallIdHeader.NAME).clone(),
                headerFactory.createCSeqHeader(cseq, Request.ACK), (FromHeader) ok.getHeader(FromHeader.NAME).clone(),
                (ToHeader) ok.getHeader(ToHeader.NAME).clone(), vias, headerFactory.createMaxForwardsHeader(70));
        sipProvider.sendRequest(ack);
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        received.add(FallbackTest.describe(request));
        logger.logInfo("shootist:" + port + " received " + FallbackTest.describe(request));
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            st.sendResponse(messageFactory.createResponse(Response.OK, request));
            byeSeen = true;
        } catch (Exception ex) {
            logger.logError("shootist: unexpected exception processing " + request.getMethod(), ex);
        }
    }

    public void processIOException(IOExceptionEvent e) {
        if (Fallback.fallBack(e, sipStack, sipProvider, myAddress, port)) {
            fallbacks.incrementAndGet();
        } else {
            errors.incrementAndGet();
            logger.logError("shootist: IOException:" + e);
        }
    }

    public void processTimeout(TimeoutEvent e) {
        errors.incrementAndGet();
        logger.logError("shootist: timeout " + e.getTimeout());
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        errors.incrementAndGet();
        logger.logError("shootist: dialog timeout " + timeoutEvent.getReason());
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.logInfo("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        dialogsTerminated.incrementAndGet();
        logger.logInfo("dialog terminated event recieved:" + e);
    }

    /** what reached the listener, retransmitted responses left out */
    public List<String> getReceived() {
        synchronized (received) {
            return new ArrayList<String>(received);
        }
    }

    public int getFallbacks() {
        return fallbacks.get();
    }

    public int getErrors() {
        return errors.get();
    }

    public int getDialogsTerminated() {
        return dialogsTerminated.get();
    }

    public void stop() {
        sipStack.stop();
    }

    public TestAssertion getCallAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return okSeen && byeSeen;
            }
        };
    }

    public TestAssertion getCleanAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return sipStack.getClientTransactionTableSize() == 0 && sipStack.getServerTransactionTableSize() == 0
                        && sipStack.getDialogs().isEmpty();
            }
        };
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("caller", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(ListeningPoint.UDP);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }
}
