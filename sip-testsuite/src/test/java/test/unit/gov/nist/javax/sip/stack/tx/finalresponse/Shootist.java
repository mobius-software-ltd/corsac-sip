package test.unit.gov.nist.javax.sip.stack.tx.finalresponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.SipStackImpl;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Records the responses its listener gets and its client transaction terminated
 * events, in arrival order.
 */
public class Shootist implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    static final String TERMINATED = "terminated";

    private final int port;
    private final int peerPort;
    private final String transport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    /** Create the dialog before sending the INVITE, as the SIP RA does for dialog activities. */
    public boolean appDialog;

    /** Response status codes and client transaction terminated events, in arrival order. */
    private final List<String> events = Collections.synchronizedList(new ArrayList<String>());

    private volatile boolean timeoutSeen;
    private volatile boolean ioExceptionSeen;

    public Shootist(int port, int peerPort, String transport, StackSettings settings) {
        this.port = port;
        this.peerPort = peerPort;
        this.transport = transport;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport,
                settings.automaticDialog(), false, settings.reentrantListener());
        if (settings.aggressiveCleanup()) {
            ((SipStackImpl) protocolObjects.sipStack).setAggressiveCleanup(true);
        }
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootist", ex);
        }
    }

    public void sendInvite() {
        sendRequest(Request.INVITE);
    }

    public void sendRequest(String method) {
        try {
            SipURI fromUri = addressFactory.createSipURI("caller", "finalresponse.test");
            FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri),
                    "shootist-" + port);
            SipURI toUri = addressFactory.createSipURI("callee", "finalresponse.test");
            ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

            SipURI requestUri = addressFactory.createSipURI("callee", myAddress);
            requestUri.setPort(peerPort);
            requestUri.setTransportParam(transport);

            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, method);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(requestUri, method, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards);
            request.addHeader(createContact());

            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            if (appDialog && method.equals(Request.INVITE)) {
                sipProvider.getNewDialog(clientTransaction);
            }
            logger.info("shootist: sending " + method + " over " + transport);
            clientTransaction.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send " + method, ex);
        }
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("caller", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        logger.info("shootist:" + port + " received response " + status + " dialog " + responseEvent.getDialog());
        events.add(String.valueOf(status));
        try {
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            Dialog dialog = responseEvent.getDialog();
            // stack ACKs non-2xx on its own, without a dialog there is nothing to ACK a 2xx with
            if (cseq.getMethod().equals(Request.INVITE) && status / 100 == 2 && dialog != null) {
                dialog.sendAck(dialog.createAck(cseq.getSeqNumber()));
            }
        } catch (Exception ex) {
            logger.error("shootist: could not ACK " + status, ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.info("shootist:" + port + " received " + requestEvent.getRequest().getMethod());
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        timeoutSeen = true;
        logger.info("shootist: transaction timeout " + timeoutEvent.getTimeout());
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.error("shootist: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
        if (!e.isServerTransaction()) {
            events.add(TERMINATED);
        }
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public List<String> getEvents() {
        synchronized (events) {
            return new ArrayList<String>(events);
        }
    }

    public boolean isTimeoutSeen() {
        return timeoutSeen;
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public TestAssertion getResponseAssertion(final int status) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return events.contains(String.valueOf(status));
            }
        };
    }

    public void stop() {
        sipStack.stop();
    }
}
