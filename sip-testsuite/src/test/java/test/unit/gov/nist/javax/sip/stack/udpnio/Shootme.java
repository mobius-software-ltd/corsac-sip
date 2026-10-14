package test.unit.gov.nist.javax.sip.stack.udpnio;

import java.util.Properties;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;

import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Answers the INVITE with 180 and 200, records the ACK, answers the BYE with 200;
 * every message it sends is a response.
 */
public class Shootme implements SipListener {

    private static final StackLogger logger = CommonLogger.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final String toTag = "shootme";

    private final int port;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private volatile boolean inviteSeen;
    private volatile boolean ackSeen;

    public Shootme(int port, Properties stackProperties) {
        this.port = port;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", ListeningPoint.UDP,
                true, false, false, stackProperties);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, ListeningPoint.UDP);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootme", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        logger.logInfo("shootme:" + port + " received " + method);
        try {
            if (method.equals(Request.ACK)) {
                ackSeen = true;
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            if (method.equals(Request.INVITE)) {
                inviteSeen = true;
                st.sendResponse(createResponse(Response.RINGING, request));
                Response ok = createResponse(Response.OK, request);
                ok.setHeader(createContact());
                st.sendResponse(ok);
            } else {
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            }
        } catch (Exception ex) {
            logger.logError("shootme: unexpected exception processing " + method, ex);
        }
    }

    private Response createResponse(int status, Request request) throws Exception {
        Response response = messageFactory.createResponse(status, request);
        ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
        return response;
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("callee", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(ListeningPoint.UDP);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public void processResponse(ResponseEvent responseEvent) {
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        logger.logInfo("shootme: transaction timeout");
    }

    public void processIOException(IOExceptionEvent e) {
        logger.logError("shootme: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.logInfo("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.logInfo("dialog terminated event recieved:" + e);
    }

    /** UDP channels bound, when the stack runs on {@link NioUdpMessageProcessorFactory} */
    public int getNioBoundChannels() {
        return NioUdpMessageProcessorFactory.boundChannels(sipProvider);
    }

    public TestAssertion getInviteAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return inviteSeen;
            }
        };
    }

    public TestAssertion getAckAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return ackSeen;
            }
        };
    }

    public void stop() {
        sipStack.stop();
    }
}
