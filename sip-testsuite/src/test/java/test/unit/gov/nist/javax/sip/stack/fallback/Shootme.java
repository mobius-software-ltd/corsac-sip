package test.unit.gov.nist.javax.sip.stack.fallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
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
import gov.nist.javax.sip.message.MessageExt;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Answers the INVITE with 183 and 200, hangs up with a BYE after the ACK (by hand without dialogs).
 * UDP and TCP on the same port, a UDP message over the limit goes again over TCP.
 */
public class Shootme implements SipListenerExt {

    private static final StackLogger logger = CommonLogger.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final long DELAY = 200;

    private final int port;
    private final boolean dialogs;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStackImpl sipStack;
    private final SipProviderImpl sipProvider;

    private final Timer timer = new Timer();

    /** text body on the 200 to the INVITE, 0 for none */
    public int okBodySize;

    private volatile Request invite;
    private volatile ServerTransaction inviteTransaction;
    private final String toTag = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));

    private final List<String> received = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicInteger fallbacks = new AtomicInteger();
    /** other IOExceptions, timeouts, dialog timeouts */
    private final AtomicInteger errors = new AtomicInteger();
    private final AtomicInteger dialogsTerminated = new AtomicInteger();
    private volatile boolean ackSeen;
    private volatile boolean byeOkSeen;

    public Shootme(int port, boolean dialogs, Properties stackProperties) {
        this.port = port;
        this.dialogs = dialogs;
        ProtocolObjects protocolObjects = new ProtocolObjects("fallback-shootme-" + port, "gov.nist",
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
            throw new RuntimeException("could not create shootme", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        received.add(FallbackTest.describe(request));
        logger.logInfo("shootme:" + port + " received " + FallbackTest.describe(request));
        try {
            if (request.getMethod().equals(Request.ACK)) {
                if (!ackSeen) {
                    ackSeen = true;
                    schedule(new Runnable() {
                        @Override
                        public void run() {
                            sendBye();
                        }
                    });
                }
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            if (!request.getMethod().equals(Request.INVITE)) {
                st.sendResponse(messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, request));
                return;
            }
            invite = request;
            inviteTransaction = st;
            st.sendResponse(createResponse(Response.SESSION_PROGRESS));
            schedule(new Runnable() {
                @Override
                public void run() {
                    sendOk();
                }
            });
        } catch (Exception ex) {
            logger.logError("shootme: unexpected exception processing " + request.getMethod(), ex);
        }
    }

    private void sendOk() {
        try {
            Response ok = createResponse(Response.OK);
            if (okBodySize > 0) {
                ok.setContent(FallbackTest.body(okBodySize), headerFactory.createContentTypeHeader("text", "plain"));
            }
            inviteTransaction.sendResponse(ok);
        } catch (Exception ex) {
            logger.logError("shootme: could not send 200", ex);
        }
    }

    private void sendBye() {
        try {
            if (dialogs) {
                Dialog dialog = inviteTransaction.getDialog();
                dialog.sendRequest(sipProvider.getNewClientTransaction(dialog.createRequest(Request.BYE)));
            } else {
                sipProvider.getNewClientTransaction(createBye()).sendRequest();
            }
        } catch (Exception ex) {
            logger.logError("shootme: could not send BYE", ex);
        }
    }

    /** no dialog: BYE to the caller's Contact, From and To swapped, own CSeq */
    private Request createBye() throws Exception {
        FromHeader inviteFrom = (FromHeader) invite.getHeader(FromHeader.NAME);
        ToHeader inviteTo = (ToHeader) invite.getHeader(ToHeader.NAME);
        List<ViaHeader> vias = new ArrayList<ViaHeader>();
        vias.add(headerFactory.createViaHeader(myAddress, port, ListeningPoint.UDP, null));
        return messageFactory.createRequest(((ContactHeader) invite.getHeader(ContactHeader.NAME)).getAddress().getURI(),
                Request.BYE, (CallIdHeader) invite.getHeader(CallIdHeader.NAME).clone(),
                headerFactory.createCSeqHeader(1L, Request.BYE),
                headerFactory.createFromHeader(inviteTo.getAddress(), toTag),
                headerFactory.createToHeader(inviteFrom.getAddress(), inviteFrom.getTag()), vias,
                headerFactory.createMaxForwardsHeader(70));
    }

    private Response createResponse(int status) throws Exception {
        Response response = messageFactory.createResponse(status, invite);
        ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
        SipURI contactUri = addressFactory.createSipURI("callee", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(ListeningPoint.UDP);
        response.addHeader(headerFactory.createContactHeader(addressFactory.createAddress(contactUri)));
        return response;
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        if (responseEvent instanceof ResponseEventExt && ((ResponseEventExt) responseEvent).isRetransmission()) {
            return;
        }
        received.add(FallbackTest.describe(response));
        logger.logInfo("shootme:" + port + " received " + FallbackTest.describe(response));
        if (response.getStatusCode() == Response.OK) {
            byeOkSeen = true;
        }
    }

    public void processIOException(IOExceptionEvent e) {
        if (Fallback.fallBack(e, sipStack, sipProvider, myAddress, port)) {
            fallbacks.incrementAndGet();
        } else {
            errors.incrementAndGet();
            logger.logError("shootme: IOException:" + e);
        }
    }

    public void processTimeout(TimeoutEvent e) {
        errors.incrementAndGet();
        logger.logError("shootme: timeout " + e.getTimeout());
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        errors.incrementAndGet();
        logger.logError("shootme: dialog timeout " + timeoutEvent.getReason());
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
        timer.cancel();
        sipStack.stop();
    }

    public TestAssertion getCallAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return ackSeen && byeOkSeen;
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

    private void schedule(final Runnable task) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, DELAY);
    }
}
