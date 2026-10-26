package test.b2b.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
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
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.ResponseEventExt;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
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
    private ListeningPoint listeningPoint;

    private final Timer timer = new Timer();

    public boolean requireReliableProvisionalResponse;
    public long byeDelay = 500;
    public long reInviteDelay;
    public int answerReInviteWith = 200;
    private volatile boolean reInviteReceived;

    private Dialog dialog;
    private volatile boolean initialAcked;

    private volatile boolean inviteOkSeen;
    private volatile boolean byeOkSeen;
    private volatile int reliableProvisionalCount;
    private volatile int prackOkCount;
    private volatile int reInviteFinalStatus;
    private volatile int keepAliveOkCount;
    private final Set<String> okDialogIds = Collections.synchronizedSet(new HashSet<String>());

    public Shootist(int port, int peerPort) {
        this.port = port;
        this.peerPort = peerPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, true,
                false, false);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            this.listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootist", ex);
        }
    }





    public void sendInvite() {
        try {
            SipURI fromUri = addressFactory.createSipURI("alice", "test.mobius.local");
            Address fromAddress = addressFactory.createAddress(fromUri);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "caller-" + port);

            SipURI toUri = addressFactory.createSipURI("bob", "test.mobius.local");
            ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

            SipURI requestUri = addressFactory.createSipURI("bob", myAddress);
            requestUri.setPort(peerPort);

            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards);

            request.addHeader(createContact());

            SipURI routeUri = addressFactory.createSipURI(null, myAddress);
            routeUri.setPort(peerPort);
            routeUri.setLrParam();
            RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeUri));
            request.setHeader(routeHeader);

            if (requireReliableProvisionalResponse) {
                request.addHeader(headerFactory.createRequireHeader("100rel"));
            }

            ClientTransaction inviteTransaction = sipProvider.getNewClientTransaction(request);
            logger.info("shootist: sending INVITE" + (requireReliableProvisionalResponse ? " requiring 100rel" : ""));
            inviteTransaction.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send INVITE", ex);
        }
    }

    /** Start pinging the B2BUA with OPTIONS keepalives at the given interval. */
    public void startKeepAlive(long intervalMs) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendKeepAlive();
            }
        }, 0, intervalMs);
    }

    private void sendKeepAlive() {
        try {
            SipURI requestUri = addressFactory.createSipURI("b2b", myAddress);
            requestUri.setPort(peerPort);

            SipURI fromUri = addressFactory.createSipURI("alice", "test.mobius.local");
            FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri),
                    Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));
            SipURI toUri = addressFactory.createSipURI("b2b", "test.mobius.local");
            ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

            Request options = messageFactory.createRequest(requestUri, Request.OPTIONS, sipProvider.getNewCallId(),
                    headerFactory.createCSeqHeader(1L, Request.OPTIONS), fromHeader, toHeader, viaHeaders,
                    headerFactory.createMaxForwardsHeader(70));
            sipProvider.getNewClientTransaction(options).sendRequest();
        } catch (Exception ex) {
            logger.error("shootist: could not send keepalive", ex);
        }
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("alice", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public synchronized void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
        if (!responseEventExt.isForkedResponse() && responseEventExt.isRetransmission()) {
            return;
        }
        logger.info("shootist: received " + status + " for " + cseq.getMethod());
        try {
            if (cseq.getMethod().equals(Request.OPTIONS)) {
                if (status == Response.OK) {
                    keepAliveOkCount++;
                }
                return;
            }
            if (cseq.getMethod().equals(Request.BYE)) {
                if (status == Response.OK) {
                    byeOkSeen = true;
                }
                return;
            }
            if (cseq.getMethod().equals(Request.PRACK)) {
                if (status == Response.OK) {
                    prackOkCount++;
                }
                return;
            }
            if (!cseq.getMethod().equals(Request.INVITE)) {
                return;
            }
            Dialog responseDialog = responseEvent.getDialog();
            if (status == Response.SESSION_PROGRESS) {
                RequireHeader require = (RequireHeader) response.getHeader(RequireHeader.NAME);
                if (require != null && require.getOptionTag().equalsIgnoreCase("100rel")) {
                    reliableProvisionalCount++;
                    Request prack = responseDialog.createPrack(response);
                    ClientTransaction prackTransaction = sipProvider.getNewClientTransaction(prack);
                    responseDialog.sendRequest(prackTransaction);
                }
            } else if (status == Response.OK) {
                okDialogIds.add(responseDialog.getDialogId());
                Request ack = responseDialog.createAck(cseq.getSeqNumber());
                // Thread.sleep(3000);
                responseDialog.sendAck(ack);
                if (!initialAcked) {
                    initialAcked = true;
                    inviteOkSeen = true;
                    this.dialog = responseDialog;
                    if (reInviteDelay > 0) {
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                sendReInvite();
                            }
                        }, reInviteDelay);
                    } else {
                        scheduleBye();
                    }
                } else if (cseq.getSeqNumber() > 1) {
                    reInviteFinalStatus = Response.OK;
                    scheduleBye();
                }
            } else if (status >= 300 && cseq.getSeqNumber() > 1) {
                // stack ACKs the error itself
                reInviteFinalStatus = status;
                scheduleBye();
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception processing " + status, ex);
        }
    }

    private void sendReInvite() {
        try {
            Request reInvite = dialog.createRequest(Request.INVITE);
            reInvite.setHeader(createContact());
            ClientTransaction reInviteTransaction = sipProvider.getNewClientTransaction(reInvite);
            logger.info("shootist: sending re-INVITE");
            dialog.sendRequest(reInviteTransaction);
        } catch (Exception ex) {
            logger.error("shootist: could not send re-INVITE", ex);
        }
    }

    private void scheduleBye() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    Request bye = dialog.createRequest(Request.BYE);
                    ClientTransaction byeTransaction = sipProvider.getNewClientTransaction(bye);
                    logger.info("shootist: sending BYE");
                    dialog.sendRequest(byeTransaction);
                } catch (Exception ex) {
                    logger.error("shootist: could not send BYE", ex);
                }
            }
        }, byeDelay);
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        logger.info("shootist: received request " + request.getMethod());
        try {
            if (request.getMethod().equals(Request.BYE)) {
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
                }
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else if (request.getMethod().equals(Request.INVITE)) {
                // reINVITE from the peer
                reInviteReceived = true;
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
                }
                Response response = messageFactory.createResponse(answerReInviteWith, request);
                if (answerReInviteWith == Response.OK) {
                    response.setHeader(createContact());
                }
                logger.info("shootist: answering re-INVITE with " + answerReInviteWith);
                st.sendResponse(response);
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("shootist: transaction timeout:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("shootist: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isInviteOkSeen() {
        return inviteOkSeen;
    }

    public boolean isByeOkSeen() {
        return byeOkSeen;
    }

    public int getReliableProvisionalCount() {
        return reliableProvisionalCount;
    }

    public int getPrackOkCount() {
        return prackOkCount;
    }

    public int getReInviteFinalStatus() {
        return reInviteFinalStatus;
    }

    public boolean isReInviteReceived() {
        return reInviteReceived;
    }

    public int getKeepAliveOkCount() {
        return keepAliveOkCount;
    }

    public int getOkDialogCount() {
        return okDialogIds.size();
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
