package test.unit.gov.nist.javax.sip.stack.tx.finalresponse;

import java.util.Timer;
import java.util.TimerTask;

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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Answers an INVITE with 100, an optional 180 and a final response, a non-INVITE
 * with the final response. Automatic dialog support is off, so the To tags are the
 * ones set here.
 */
public class Shootme implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final String transport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    public boolean sendRinging = true;
    /** null = untagged 180. */
    public String ringingToTag = "shootme";
    public int finalResponse = Response.TEMPORARILY_UNAVAILABLE;
    /** null = untagged final response. */
    public String finalToTag = "shootme";
    /** Between the 100, the 180 and the final response. */
    public long responseDelay = 100;

    private volatile boolean inviteSeen;

    public Shootme(int port, String transport) {
        this.port = port;
        this.transport = transport;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, false, false,
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
            throw new RuntimeException("could not create shootme", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        logger.info("shootme:" + port + " received " + method);
        if (method.equals(Request.ACK)) {
            return;
        }
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            if (method.equals(Request.INVITE)) {
                inviteSeen = true;
                st.sendResponse(messageFactory.createResponse(Response.TRYING, request));
                long delay = responseDelay;
                if (sendRinging) {
                    scheduleResponse(st, request, Response.RINGING, ringingToTag, delay);
                    delay += responseDelay;
                }
                scheduleResponse(st, request, finalResponse, finalToTag, delay);
            } else {
                st.sendResponse(createResponse(request, finalResponse, finalToTag));
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing " + method, ex);
        }
    }

    private void scheduleResponse(final ServerTransaction st, final Request request, final int status,
            final String toTag, long delay) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    logger.info("shootme: sending " + status + (toTag == null ? " untagged" : " tag " + toTag));
                    st.sendResponse(createResponse(request, status, toTag));
                } catch (Exception ex) {
                    logger.error("shootme: could not send " + status, ex);
                }
            }
        }, delay);
    }

    private Response createResponse(Request request, int status, String toTag) throws Exception {
        Response response = messageFactory.createResponse(status, request);
        if (toTag != null) {
            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
        }
        if (status / 100 == 2 && request.getMethod().equals(Request.INVITE)) {
            response.setHeader(createContact());
        }
        return response;
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("callee", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public void processResponse(ResponseEvent responseEvent) {
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        logger.info("shootme: transaction timeout");
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("shootme: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isInviteSeen() {
        return inviteSeen;
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
