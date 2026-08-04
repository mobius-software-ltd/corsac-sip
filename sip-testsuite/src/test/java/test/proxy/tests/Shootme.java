package test.proxy.tests;

import java.util.Hashtable;
import java.util.Random;
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
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.header.extensions.SessionExpires;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 */
public class Shootme implements SipListener {

    public enum Behavior {
        ANSWER, RING_ONLY, BUSY, REDIRECT
    }

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final Behavior behavior;
    private final long okDelay;
    public int redirectPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();
    private String toTag;
    private final Hashtable<String, ServerTransaction> serverTxTable = new Hashtable<String, ServerTransaction>();

    private volatile boolean inviteSeen;
    private volatile boolean reInviteSeen;
    private volatile boolean ackSeen;
    private volatile boolean byeSeen;
    private volatile boolean cancelSeen;
    private volatile boolean optionsReceived;

    public Shootme(int port, Behavior behavior, long okDelay) {
        this.port = port;
        this.behavior = behavior;
        this.okDelay = okDelay;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, true, false,
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
        try {
            if (method.equals(Request.INVITE)) {
                processInvite(requestEvent);
            } else if (method.equals(Request.ACK)) {
                ackSeen = true;
            } else if (method.equals(Request.BYE)) {
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
                }
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
                byeSeen = true;
            } else if (method.equals(Request.CANCEL)) {
                processCancel(requestEvent);
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
            // reINVITE
            reInviteSeen = true;
            serverTransaction.sendResponse(createOk(request));
            return;
        }
        inviteSeen = true;
        if (toTag == null) {
            toTag = Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));
        }
        String branch = ((ViaHeader) request.getHeader(ViaHeader.NAME)).getBranch();
        serverTxTable.put(branch, serverTransaction);

        switch (behavior) {
            case BUSY:
                Response busy = messageFactory.createResponse(Response.BUSY_HERE, request);
                ((ToHeader) busy.getHeader(ToHeader.NAME)).setTag(toTag);
                serverTransaction.sendResponse(busy);
                break;
            case REDIRECT:
                Response moved = messageFactory.createResponse(Response.MOVED_TEMPORARILY, request);
                ((ToHeader) moved.getHeader(ToHeader.NAME)).setTag(toTag);
                SipURI redirectUri = addressFactory.createSipURI(null, myAddress);
                redirectUri.setPort(redirectPort);
                moved.setHeader(headerFactory.createContactHeader(addressFactory.createAddress(redirectUri)));
                serverTransaction.sendResponse(moved);
                break;
            case RING_ONLY:
                serverTransaction.sendResponse(createRinging(request));
                break;
            case ANSWER:
                serverTransaction.sendResponse(createRinging(request));
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            if (serverTransaction.getState() != TransactionState.COMPLETED
                                    && serverTransaction.getState() != TransactionState.TERMINATED) {
                                serverTransaction.sendResponse(createOk(request));
                            }
                        } catch (Exception ex) {
                            logger.error("shootme: could not send OK", ex);
                        }
                    }
                }, okDelay);
                break;
        }
    }

    private Response createRinging(Request request) throws Exception {
        Response ringing = messageFactory.createResponse(Response.RINGING, request);
        ((ToHeader) ringing.getHeader(ToHeader.NAME)).setTag(toTag);
        return ringing;
    }

    private Response createOk(Request request) throws Exception {
        Response ok = messageFactory.createResponse(Response.OK, request);
        ToHeader toHeader = (ToHeader) ok.getHeader(ToHeader.NAME);
        if (toHeader.getTag() == null) {
            toHeader.setTag(toTag);
        }
        ok.setHeader(createContact());
        Header sessionExpires = request.getHeader(SessionExpires.NAME);
        if (sessionExpires != null) {
            ok.setHeader((Header) sessionExpires.clone());
        }
        return ok;
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("callee", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        Address contactAddress = addressFactory.createAddress(contactUri);
        return headerFactory.createContactHeader(contactAddress);
    }

    private void processCancel(RequestEvent requestEvent) throws Exception {
        Request cancel = requestEvent.getRequest();
        cancelSeen = true;
        ServerTransaction cancelTx = requestEvent.getServerTransaction();
        if (cancelTx == null) {
            cancelTx = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(cancel);
        }
        Response cancelOk = messageFactory.createResponse(Response.OK, cancel);
        ((ToHeader) cancelOk.getHeader(ToHeader.NAME)).setTag(toTag);
        cancelTx.sendResponse(cancelOk);

        String branch = ((ViaHeader) cancel.getHeader(ViaHeader.NAME)).getBranch();
        ServerTransaction inviteTx = serverTxTable.get(branch);
        if (inviteTx != null && (inviteTx.getState() == TransactionState.TRYING
                || inviteTx.getState() == TransactionState.PROCEEDING)) {
            Response terminated = messageFactory.createResponse(Response.REQUEST_TERMINATED, inviteTx.getRequest());
            ((ToHeader) terminated.getHeader(ToHeader.NAME)).setTag(toTag);
            inviteTx.sendResponse(terminated);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        logger.info("shootme:" + port + " received response " + responseEvent.getResponse().getStatusCode());
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

    public boolean isAckSeen() {
        return ackSeen;
    }

    public boolean isByeSeen() {
        return byeSeen;
    }

    public boolean isCancelSeen() {
        return cancelSeen;
    }

    public boolean isOptionsReceived() {
        return optionsReceived;
    }

    public TestAssertion getCompletedCallAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return inviteSeen && ackSeen && byeSeen;
            }
        };
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
