package test.b2b.tests;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.Dialog;
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
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
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
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 */
public class Shootme implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    public boolean sendReliableProvisionalResponse;
    public long okDelay = 200;
    public int reInviteResponseStatus = Response.OK;
    public long reInviteAfterAck;

    private String toTag;
    private volatile boolean okScheduled;
    private volatile boolean reInviteSent;
    private Dialog dialog;

    private volatile boolean inviteSeen;
    private volatile boolean reInviteSeen;
    private volatile int prackReceivedCount;
    private volatile boolean ackSeen;
    private volatile boolean byeSeen;
    private volatile int reInviteFinalStatus;
    private volatile boolean optionsReceived;

    public Shootme(int port) {
        this.port = port;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, true,
                false, false);
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
        try {
            if (method.equals(Request.INVITE)) {
                processInvite(requestEvent);
            } else if (method.equals(Request.PRACK)) {
                processPrack(requestEvent);
            } else if (method.equals(Request.ACK)) {
                ackSeen = true;
                if (reInviteAfterAck > 0 && !reInviteSent) {
                    reInviteSent = true;
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            sendReInvite();
                        }
                    }, reInviteAfterAck);
                }
            } else if (method.equals(Request.BYE)) {
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
                }
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
                byeSeen = true;
            } else if (method.equals(Request.OPTIONS)) {
                // keepalives must never cross end-to-end
                optionsReceived = true;
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing " + method, ex);
        }
    }

    private void processInvite(RequestEvent requestEvent) throws Exception {
        final Request request = requestEvent.getRequest();
        ServerTransaction st = requestEvent.getServerTransaction();
        if (st == null) {
            st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
        }
        final ServerTransaction serverTransaction = st;
        String requestToTag = ((ToHeader) request.getHeader(ToHeader.NAME)).getTag();
        if (requestToTag != null) {
            reInviteSeen = true;
            Response response = messageFactory.createResponse(reInviteResponseStatus, request);
            if (reInviteResponseStatus == Response.OK) {
                response.setHeader(createContact());
            }
            logger.info("shootme: answering re-INVITE with " + reInviteResponseStatus);
            serverTransaction.sendResponse(response);
            return;
        }
        inviteSeen = true;
        this.dialog = serverTransaction.getDialog();
        if (toTag == null) {
            toTag = Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));
        }
        if (sendReliableProvisionalResponse) {
            Response sessionProgress = messageFactory.createResponse(Response.SESSION_PROGRESS, request);
            ((ToHeader) sessionProgress.getHeader(ToHeader.NAME)).setTag(toTag);
            sessionProgress.setHeader(createContact());
            sessionProgress.addHeader(headerFactory.createRequireHeader("100rel"));
            Dialog dialog = serverTransaction.getDialog();
            // the PRACK handler needs the invite stx later
            dialog.setApplicationData(serverTransaction);
            logger.info("shootme: sending reliable 183");
            dialog.sendReliableProvisionalResponse(sessionProgress);
        } else {
            Response ringing = messageFactory.createResponse(Response.RINGING, request);
            ((ToHeader) ringing.getHeader(ToHeader.NAME)).setTag(toTag);
            serverTransaction.sendResponse(ringing);
            scheduleOk(request, serverTransaction);
        }
    }

    private void processPrack(RequestEvent requestEvent) throws Exception {
        prackReceivedCount++;
        Request prack = requestEvent.getRequest();
        ServerTransaction st = requestEvent.getServerTransaction();
        st.sendResponse(messageFactory.createResponse(Response.OK, prack));
        // 183 is PRACKed, the OK can go now
        Dialog dialog = st.getDialog();
        ServerTransaction inviteTransaction = (ServerTransaction) dialog.getApplicationData();
        if (inviteTransaction != null && !okScheduled) {
            scheduleOk(inviteTransaction.getRequest(), inviteTransaction);
        }
    }

    private void scheduleOk(final Request request, final ServerTransaction serverTransaction) {
        okScheduled = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (serverTransaction.getState() != TransactionState.COMPLETED
                            && serverTransaction.getState() != TransactionState.TERMINATED) {
                        Response ok = messageFactory.createResponse(Response.OK, request);
                        ((ToHeader) ok.getHeader(ToHeader.NAME)).setTag(toTag);
                        ok.setHeader(createContact());
                        serverTransaction.sendResponse(ok);
                    }
                } catch (Exception ex) {
                    logger.error("shootme: could not send OK", ex);
                }
            }
        }, okDelay);
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("callee", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        Address contactAddress = addressFactory.createAddress(contactUri);
        return headerFactory.createContactHeader(contactAddress);
    }

    public void sendReInvite() {
        try {
            Request reInvite = dialog.createRequest(Request.INVITE);
            reInvite.setHeader(createContact());
            logger.info("shootme: sending re-INVITE");
            dialog.sendRequest(sipProvider.getNewClientTransaction(reInvite));
        } catch (Exception ex) {
            logger.error("shootme: could not send re-INVITE", ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        logger.info("shootme:" + port + " received response " + response.getStatusCode());
        try {
            javax.sip.header.CSeqHeader cseq = (javax.sip.header.CSeqHeader) response
                    .getHeader(javax.sip.header.CSeqHeader.NAME);
            if (cseq.getMethod().equals(Request.INVITE) && response.getStatusCode() >= 200) {
                reInviteFinalStatus = response.getStatusCode();
                if (response.getStatusCode() == Response.OK) {
                    Dialog responseDialog = responseEvent.getDialog();
                    responseDialog.sendAck(responseDialog.createAck(cseq.getSeqNumber()));
                }
                // stack ACKs non-2xx on its own
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing response", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
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

    public boolean isReInviteSeen() {
        return reInviteSeen;
    }

    public int getPrackReceivedCount() {
        return prackReceivedCount;
    }

    public boolean isAckSeen() {
        return ackSeen;
    }

    public boolean isByeSeen() {
        return byeSeen;
    }

    public int getReInviteFinalStatus() {
        return reInviteFinalStatus;
    }

    public boolean isOptionsReceived() {
        return optionsReceived;
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
