package test.unit.gov.nist.javax.sip.stack.messagesize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.message.MessageExt;
import gov.nist.javax.sip.message.SIPMessage;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Sends a MESSAGE padded to an exact size, records the size that actually went
 * out (processMessageSent) and the response.
 */
public class Shootist implements SipListenerExt {

    private static final StackLogger logger = CommonLogger.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int peerPort;
    private final String transport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    /** size of every MESSAGE the stack sent */
    private final List<Integer> sentSizes = Collections.synchronizedList(new ArrayList<Integer>());
    private final List<Integer> receivedStatuses = Collections.synchronizedList(new ArrayList<Integer>());

    private volatile boolean ioExceptionSeen;

    /** no client transaction: the MESSAGE goes out once, a dropped datagram is never retransmitted */
    public boolean sendOnce;

    public Shootist(int port, int peerPort, String transport) {
        this.port = port;
        this.peerPort = peerPort;
        this.transport = transport;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, true, false,
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

    /** MESSAGE of exactly size bytes on the wire */
    public void sendMessage(int size) {
        try {
            SipURI fromUri = addressFactory.createSipURI("sender", "messagesize.test");
            FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri),
                    Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));
            SipURI toUri = addressFactory.createSipURI("receiver", "messagesize.test");
            ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

            SipURI requestUri = addressFactory.createSipURI("receiver", myAddress);
            requestUri.setPort(peerPort);
            requestUri.setTransportParam(transport);

            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

            Request request = messageFactory.createRequest(requestUri, Request.MESSAGE, sipProvider.getNewCallId(),
                    headerFactory.createCSeqHeader(1L, Request.MESSAGE), fromHeader, toHeader, viaHeaders,
                    headerFactory.createMaxForwardsHeader(70));
            // the client transaction adds the Via branch, so the body gets sized after that
            ClientTransaction ct = null;
            if (sendOnce) {
                viaHeaders.get(0).setBranch("z9hG4bK" + Long.toHexString(System.nanoTime()));
            } else {
                ct = sipProvider.getNewClientTransaction(request);
            }

            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("text", "plain");
            int bodyLength = 0;
            // Content-Length digits change with the body, a few rounds to get it exact
            for (int i = 0; i < 5; i++) {
                request.setContent(padding(bodyLength), contentType);
                int missing = size - encodedSize(request);
                if (missing == 0) {
                    if (ct != null) {
                        ct.sendRequest();
                    } else {
                        sipProvider.sendRequest(request);
                    }
                    return;
                }
                bodyLength += missing;
            }
            throw new IllegalStateException("cannot pad a MESSAGE to " + size + " bytes");
        } catch (Exception ex) {
            throw new RuntimeException("could not send MESSAGE", ex);
        }
    }

    private static String padding(int length) {
        char[] body = new char[length];
        Arrays.fill(body, 'a');
        return new String(body);
    }

    private int encodedSize(Message message) {
        return ((SIPMessage) message).encodeAsBytes(transport).length;
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.logInfo("shootist:" + port + " unexpected request " + requestEvent.getRequest().getMethod());
    }

    public void processResponse(ResponseEvent responseEvent) {
        int status = responseEvent.getResponse().getStatusCode();
        receivedStatuses.add(status);
        logger.logInfo("shootist:" + port + " received " + status);
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
        if (messageSentEvent instanceof Request) {
            int size = encodedSize(messageSentEvent);
            sentSizes.add(size);
            logger.logInfo("shootist:" + port + " sent " + ((Request) messageSentEvent).getMethod() + " of " + size
                    + " bytes");
        }
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        logger.logInfo("dialog timeout event:" + timeoutEvent.getReason());
    }

    public void processTimeout(TimeoutEvent e) {
        logger.logInfo("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.logError("shootist: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.logInfo("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.logInfo("dialog terminated event recieved:" + e);
    }

    /** -1 when nothing was sent yet */
    public int getSentSize() {
        synchronized (sentSizes) {
            if (sentSizes.isEmpty()) {
                return -1;
            }
            return sentSizes.get(0);
        }
    }

    /** -1 when no response came */
    public int getFirstStatus() {
        synchronized (receivedStatuses) {
            if (receivedStatuses.isEmpty()) {
                return -1;
            }
            return receivedStatuses.get(0);
        }
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public void stop() {
        sipStack.stop();
    }

    public TestAssertion getSentAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !sentSizes.isEmpty();
            }
        };
    }

    public TestAssertion getResponseAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !receivedStatuses.isEmpty();
            }
        };
    }
}
