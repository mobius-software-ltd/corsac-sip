package test.unit.gov.nist.javax.sip.stack.retransmissiontimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sip.ClientTransaction;
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
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.SipStackImpl;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;
import test.unit.gov.nist.javax.sip.stack.retransmissiontimer.Records.MessageRecord;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Sends one out-of-dialog NOTIFY or INVITE, no automatic dialog support, like the SIP RA.
 */
public class Shootist implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final int peerPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    /** T1 in ms for our client transactions; 50 brings Timer F and D down to 3.2 s, T2 to 400 ms. */
    public int retransmitTimer = 50;

    private volatile ClientTransaction clientTransaction;

    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());

    private volatile boolean timeoutSeen;
    private volatile boolean ioExceptionSeen;

    public Shootist(int port, int peerPort) {
        this.port = port;
        this.peerPort = peerPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, false, false,
                false);
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

    public void sendNotify() {
        send(Request.NOTIFY);
    }

    public void sendInvite() {
        send(Request.INVITE);
    }

    private void send(String method) {
        try {
            SipURI requestUri = addressFactory.createSipURI("shootme", myAddress + ":" + peerPort);
            requestUri.setTransportParam(transport);
            SipURI fromUri = addressFactory.createSipURI("shootist", myAddress + ":" + port);
            Address fromAddress = addressFactory.createAddress(fromUri);
            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

            Request request = messageFactory.createRequest(requestUri, method, sipProvider.getNewCallId(),
                    headerFactory.createCSeqHeader(1L, method),
                    headerFactory.createFromHeader(fromAddress,
                            Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE))),
                    headerFactory.createToHeader(addressFactory.createAddress(requestUri), null), viaHeaders,
                    headerFactory.createMaxForwardsHeader(70));
            request.addHeader(headerFactory.createContactHeader(fromAddress));
            if (method.equals(Request.NOTIFY)) {
                request.addHeader(headerFactory.createEventHeader("presence"));
                request.addHeader(headerFactory.createSubscriptionStateHeader("active"));
            }

            ClientTransaction ct = sipProvider.getNewClientTransaction(request);
            ct.setRetransmitTimer(retransmitTimer);
            clientTransaction = ct;
            ct.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send " + method, ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        MessageRecord record = Records.record(responseEvent.getResponse());
        receivedResponses.add(record);
        logger.info("shootist:" + port + " received " + record);
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.info("shootist:" + port + " ignoring " + requestEvent.getRequest().getMethod());
    }

    public void processTimeout(TimeoutEvent e) {
        timeoutSeen = true;
        logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.error("shootist: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public ClientTransaction getClientTransaction() {
        return clientTransaction;
    }

    public List<MessageRecord> getFinalResponses(int status, String method) {
        return Records.responses(receivedResponses, status, method);
    }

    public List<MessageRecord> getProvisionalResponses(String method) {
        return Records.provisionalResponses(receivedResponses, method);
    }

    public boolean isTimeoutSeen() {
        return timeoutSeen;
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public TestAssertion getFinalResponseAssertion(final int status, final String method) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !getFinalResponses(status, method).isEmpty();
            }
        };
    }

    public TestAssertion getTimeoutAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return timeoutSeen;
            }
        };
    }

    public void stop() {
        sipStack.stop();
    }

    public int getClientTransactionTableSize() {
        return ((SipStackImpl) sipStack).getClientTransactionTableSize();
    }

    public int getServerTransactionTableSize() {
        return ((SipStackImpl) sipStack).getServerTransactionTableSize();
    }

    public TestAssertion getNoTransactionsAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getClientTransactionTableSize() == 0 && getServerTransactionTableSize() == 0;
            }
        };
    }
}
