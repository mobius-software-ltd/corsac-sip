package test.unit.gov.nist.javax.sip.stack.rfc4028;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
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
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import gov.nist.javax.sip.message.MessageExt;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;
import test.unit.gov.nist.javax.sip.stack.rfc4028.Records.MessageRecord;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Records what its stack sends (processMessageSent) and receives, so the tests can
 * look at refreshes the stack generated on its own.
 */
public class Shootist implements SipListenerExt {

    private static final Logger logger = LogManager.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    static final String OFFER_SDP = "v=0\r\n" + "o=shootist 1000 1000 IN IP4 127.0.0.1\r\n" + "s=rfc4028-offer\r\n"
            + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 20000 RTP/AVP 0\r\n" + "a=rtpmap:0 PCMU/8000\r\n";

    static final String PRACK_ANSWER_SDP = "v=0\r\n" + "o=shootist 2000 2000 IN IP4 127.0.0.1\r\n"
            + "s=rfc4028-prack-answer\r\n" + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 20002 RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n";

    static final String REINVITE_OFFER_SDP = "v=0\r\n" + "o=shootist 3000 3000 IN IP4 127.0.0.1\r\n"
            + "s=rfc4028-app-reinvite\r\n" + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 20004 RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n";

    static final String ANSWER_TO_PEER_SDP = "v=0\r\n" + "o=shootist 4000 4000 IN IP4 127.0.0.1\r\n"
            + "s=rfc4028-uac-answer\r\n" + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 20006 RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n";

    private final int port;
    private final int peerPort;
    private final SessionTimerMode mode;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    /** 0 = no Session-Expires on the INVITE. */
    public int sessionExpires = 90;
    /** INVITE goes out without a body, offer expected in the 183, answer goes in the PRACK. */
    public boolean requireReliableProvisionalResponse;
    /** Early UPDATE (no SE, no body) once the PRACK is confirmed. */
    public boolean sendUpdate;
    /** SE=30 on the INVITE (expect 422), then SE=30 on an UPDATE at midDialogUpdateDelay. */
    public boolean smallSe;
    public boolean delayAck;
    public long ackDelay = 1500;
    public boolean allowUpdate;
    /** -1 = never. */
    public long appReInviteDelay = -1;
    public boolean appReInviteWithSe;
    /** -1 = never. */
    public long byeDelay = -1;
    public long midDialogUpdateDelay = 5000;

    private volatile Dialog dialog;
    private volatile long initialInviteCseq = 1;
    private CallIdHeader callIdHeader;
    private String fromTag;
    private final Set<Long> inviteOksHandled = ConcurrentHashMap.newKeySet();
    private final Set<Long> appRequestCseqs = ConcurrentHashMap.newKeySet();
    private volatile boolean earlyUpdateSent;

    private final List<MessageRecord> sentRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<DialogTimeoutEvent.Reason> dialogTimeoutReasons = Collections
            .synchronizedList(new ArrayList<DialogTimeoutEvent.Reason>());

    private volatile boolean inviteOkSeen;
    private volatile boolean byeOkSeen;
    private volatile boolean byeReceived;
    private volatile boolean ioExceptionSeen;
    private volatile int timeoutEvents;
    private volatile int rejected422Count;
    private volatile int minSeOffered;
    private volatile boolean update422Seen;
    private volatile boolean updateOkSeen;
    private volatile int prackOkCount;
    private volatile int reliableProvisionalCount;

    public Shootist(int port, int peerPort, SessionTimerMode mode) {
        this.port = port;
        this.peerPort = peerPort;
        this.mode = mode;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, true, false,
                false, mode.stackProperties());
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
        try {
            this.callIdHeader = sipProvider.getNewCallId();
            this.fromTag = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
            sendInvite(1, smallSe ? 30 : sessionExpires, null);
        } catch (Exception ex) {
            throw new RuntimeException("could not send INVITE", ex);
        }
    }

    private void sendInvite(long cseq, int sessionInterval, Integer minSe) throws Exception {
        SipURI fromUri = addressFactory.createSipURI("caller", "rfc4028.test");
        FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri), fromTag);
        SipURI toUri = addressFactory.createSipURI("callee", "rfc4028.test");
        ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

        SipURI requestUri = addressFactory.createSipURI("callee", myAddress);
        requestUri.setPort(peerPort);

        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.INVITE);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        Request request = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader,
                fromHeader, toHeader, viaHeaders, maxForwards);
        request.addHeader(createContact());

        if (sessionInterval > 0) {
            request.addHeader(((HeaderFactoryImpl) headerFactory).createSessionExpiresHeader(sessionInterval));
        }
        if (minSe != null) {
            MinSE minSeHeader = new MinSE();
            minSeHeader.setExpires(minSe.intValue());
            request.addHeader(minSeHeader);
        }
        if (requireReliableProvisionalResponse) {
            request.addHeader(headerFactory.createRequireHeader("100rel"));
        } else {
            request.setContent(OFFER_SDP, sdpContentType());
        }
        if (allowUpdate) {
            for (String method : new String[] { Request.INVITE, Request.ACK, Request.CANCEL, Request.BYE,
                    Request.UPDATE, Request.PRACK }) {
                request.addHeader(headerFactory.createAllowHeader(method));
            }
        }

        initialInviteCseq = cseq;
        ClientTransaction inviteTransaction = sipProvider.getNewClientTransaction(request);
        logger.info("shootist:" + port + " sending INVITE cseq=" + cseq + " Session-Expires=" + sessionInterval
                + " Min-SE=" + minSe);
        inviteTransaction.sendRequest();
    }

    private void sendEarlyUpdate(Dialog earlyDialog) {
        try {
            Request update = earlyDialog.createRequest(Request.UPDATE);
            markAppRequest(update);
            ClientTransaction ct = sipProvider.getNewClientTransaction(update);
            logger.info("shootist:" + port + " sending early UPDATE");
            earlyDialog.sendRequest(ct);
        } catch (Exception ex) {
            logger.error("shootist: could not send early UPDATE", ex);
        }
    }

    private void sendSmallSeUpdate() {
        try {
            Request update = dialog.createRequest(Request.UPDATE);
            update.setHeader(((HeaderFactoryImpl) headerFactory).createSessionExpiresHeader(30));
            markAppRequest(update);
            ClientTransaction ct = sipProvider.getNewClientTransaction(update);
            logger.info("shootist:" + port + " sending mid-dialog UPDATE with Session-Expires=30");
            dialog.sendRequest(ct);
        } catch (Exception ex) {
            logger.error("shootist: could not send UPDATE", ex);
        }
    }

    private void sendAppReInvite() {
        try {
            Request reInvite = dialog.createRequest(Request.INVITE);
            reInvite.setContent(REINVITE_OFFER_SDP, sdpContentType());
            if (appReInviteWithSe) {
                SessionExpiresHeader se = ((HeaderFactoryImpl) headerFactory)
                        .createSessionExpiresHeader(sessionExpires);
                se.setRefresher("uac");
                reInvite.setHeader(se);
            }
            markAppRequest(reInvite);
            ClientTransaction ct = sipProvider.getNewClientTransaction(reInvite);
            logger.info("shootist:" + port + " sending application re-INVITE withSe=" + appReInviteWithSe);
            dialog.sendRequest(ct);
        } catch (Exception ex) {
            logger.error("shootist: could not send application re-INVITE", ex);
        }
    }

    public void sendBye() {
        try {
            Request bye = dialog.createRequest(Request.BYE);
            ClientTransaction byeTransaction = sipProvider.getNewClientTransaction(bye);
            logger.info("shootist:" + port + " sending BYE");
            dialog.sendRequest(byeTransaction);
        } catch (Exception ex) {
            logger.error("shootist: could not send BYE", ex);
        }
    }

    private void markAppRequest(Request request) {
        CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        appRequestCseqs.add(Long.valueOf(cseq.getSeqNumber()));
    }

    private void schedule(final Runnable task, long delay) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.error("shootist: scheduled task failed", t);
                }
            }
        }, delay);
    }

    public synchronized void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        boolean retransmission = responseEvent instanceof ResponseEventExt
                && ((ResponseEventExt) responseEvent).isRetransmission();
        MessageRecord record = Records.record(response, retransmission);
        receivedResponses.add(record);
        int status = response.getStatusCode();
        String method = record.method;
        logger.info("shootist:" + port + " received " + record);
        try {
            if (status == Response.TRYING) {
                return;
            }
            if (status / 100 == 1) {
                if (Request.INVITE.equals(method) && Records.hasOptionTag(response, RequireHeader.NAME, "100rel")) {
                    reliableProvisionalCount++;
                    Dialog earlyDialog = responseEvent.getDialog();
                    Request prack = earlyDialog.createPrack(response);
                    if (requireReliableProvisionalResponse && response.getRawContent() != null) {
                        prack.setContent(PRACK_ANSWER_SDP, sdpContentType());
                    }
                    ClientTransaction ct = sipProvider.getNewClientTransaction(prack);
                    earlyDialog.sendRequest(ct);
                }
                return;
            }
            if (Request.PRACK.equals(method)) {
                if (status == Response.OK) {
                    prackOkCount++;
                    if (sendUpdate && !earlyUpdateSent) {
                        earlyUpdateSent = true;
                        sendEarlyUpdate(responseEvent.getDialog());
                    }
                }
                return;
            }
            if (Request.UPDATE.equals(method)) {
                if (status == Response.OK) {
                    updateOkSeen = true;
                } else if (status == Response.SESSION_INTERVAL_TOO_SMALL) {
                    update422Seen = true;
                }
                return;
            }
            if (Request.BYE.equals(method)) {
                if (status == Response.OK) {
                    byeOkSeen = true;
                }
                return;
            }
            if (!Request.INVITE.equals(method)) {
                return;
            }
            if (status == Response.OK) {
                processInviteOk(responseEvent, record);
            } else if (status == Response.SESSION_INTERVAL_TOO_SMALL) {
                rejected422Count++;
                MinSE minSe = (MinSE) response.getHeader(MinSE.NAME);
                minSeOffered = minSe != null ? minSe.getExpires() : -1;
                final long nextCseq = record.cseq + 1;
                final int retryWith = minSeOffered > 0 ? minSeOffered : 90;
                logger.info("shootist:" + port + " 422 received, retrying with Session-Expires=" + retryWith);
                // 7.3: same Call-ID/From/To, CSeq + 1; 7.4: Min-SE on the retry
                schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sendInvite(nextCseq, retryWith, Integer.valueOf(retryWith));
                        } catch (Exception ex) {
                            logger.error("shootist: could not retry INVITE", ex);
                        }
                    }
                }, 200);
            } else {
                logger.info("shootist:" + port + " INVITE final response " + status + " (stack handles the ACK)");
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception processing response " + status, ex);
        }
    }

    private void processInviteOk(ResponseEvent responseEvent, MessageRecord record) throws Exception {
        final Dialog okDialog = responseEvent.getDialog();
        final long cseq = record.cseq;
        if (!inviteOksHandled.add(Long.valueOf(cseq))) {
            logger.info("shootist:" + port + " retransmitted 200 for INVITE cseq=" + cseq + ", ignoring");
            return;
        }
        final Request ack = okDialog.createAck(cseq);
        Runnable sendAck = new Runnable() {
            @Override
            public void run() {
                try {
                    okDialog.sendAck(ack);
                } catch (Exception ex) {
                    logger.error("shootist: could not send ACK", ex);
                }
            }
        };
        if (delayAck) {
            schedule(sendAck, ackDelay);
        } else {
            sendAck.run();
        }
        if (cseq == initialInviteCseq && !inviteOkSeen) {
            inviteOkSeen = true;
            dialog = okDialog;
            if (smallSe) {
                schedule(new Runnable() {
                    @Override
                    public void run() {
                        sendSmallSeUpdate();
                    }
                }, midDialogUpdateDelay);
            }
            if (appReInviteDelay >= 0) {
                schedule(new Runnable() {
                    @Override
                    public void run() {
                        sendAppReInvite();
                    }
                }, appReInviteDelay);
            }
            if (byeDelay >= 0) {
                schedule(new Runnable() {
                    @Override
                    public void run() {
                        sendBye();
                    }
                }, byeDelay);
            }
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        MessageRecord record = Records.record(request);
        receivedRequests.add(record);
        logger.info("shootist:" + port + " received " + record);
        try {
            if (method.equals(Request.ACK)) {
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            if (method.equals(Request.BYE)) {
                byeReceived = true;
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else if (method.equals(Request.INVITE)) {
                // peer is the refresher and re-INVITEs us
                Response ok = messageFactory.createResponse(Response.OK, request);
                ok.addHeader(createContact());
                ok.setContent(ANSWER_TO_PEER_SDP, sdpContentType());
                st.sendResponse(ok);
            } else if (method.equals(Request.UPDATE)) {
                Response ok = messageFactory.createResponse(Response.OK, request);
                ok.addHeader(createContact());
                st.sendResponse(ok);
            } else {
                st.sendResponse(messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, request));
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception processing " + method, ex);
        }
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
        if (messageSentEvent instanceof Request) {
            MessageRecord record = Records.record(messageSentEvent);
            sentRequests.add(record);
            logger.info("shootist:" + port + " sent " + record);
        }
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        dialogTimeoutReasons.add(timeoutEvent.getReason());
        if (timeoutEvent.getReason() == DialogTimeoutEvent.Reason.SessionExpired
                && timeoutEvent.getDialog().getState() != DialogState.TERMINATED) {
            try {
                Dialog d = timeoutEvent.getDialog();
                d.sendRequest(sipProvider.getNewClientTransaction(d.createRequest(Request.BYE)));
            } catch (Exception ex) {
                logger.error("shootist: could not BYE expired session", ex);
            }
        }
    }

    public void processTimeout(TimeoutEvent e) {
        timeoutEvents++;
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

    public boolean isInviteOkSeen() {
        return inviteOkSeen;
    }

    public boolean isByeOkSeen() {
        return byeOkSeen;
    }

    public boolean isByeReceived() {
        return byeReceived;
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public boolean isUpdate422Seen() {
        return update422Seen;
    }

    public boolean isUpdateOkSeen() {
        return updateOkSeen;
    }

    public int getTimeoutEvents() {
        return timeoutEvents;
    }

    public int getRejected422Count() {
        return rejected422Count;
    }

    public int getMinSeOffered() {
        return minSeOffered;
    }

    public int getPrackOkCount() {
        return prackOkCount;
    }

    public int getReliableProvisionalCount() {
        return reliableProvisionalCount;
    }

    public boolean isSessionExpiredSeen() {
        return dialogTimeoutReasons.contains(DialogTimeoutEvent.Reason.SessionExpired);
    }

    public int getDialogTimeouts() {
        return dialogTimeoutReasons.size();
    }

    public List<MessageRecord> getReceivedResponses() {
        return receivedResponses;
    }

    /** INVITE/UPDATE the stack sent on its own: above the initial CSeq and not created by the application. */
    public List<MessageRecord> getStackRefreshes() {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (sentRequests) {
            for (MessageRecord r : sentRequests) {
                if (!r.isRequest() || r.cseq <= initialInviteCseq) {
                    continue;
                }
                if (appRequestCseqs.contains(Long.valueOf(r.cseq))) {
                    continue;
                }
                if (Request.INVITE.equals(r.method) || Request.UPDATE.equals(r.method)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    public List<MessageRecord> getAppRequests() {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (sentRequests) {
            for (MessageRecord r : sentRequests) {
                if (r.isRequest() && appRequestCseqs.contains(Long.valueOf(r.cseq))
                        && (Request.INVITE.equals(r.method) || Request.UPDATE.equals(r.method))) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    public List<MessageRecord> getSentByes() {
        return Records.requests(sentRequests, Request.BYE);
    }

    public MessageRecord getFirstInviteOk() {
        for (MessageRecord r : getInviteOks()) {
            if (r.cseq == initialInviteCseq) {
                return r;
            }
        }
        return null;
    }

    /** one per CSeq, retransmissions folded */
    public List<MessageRecord> getInviteOks() {
        return Records.distinctResponses(receivedResponses, Response.OK, Request.INVITE);
    }

    public List<MessageRecord> getUpdateOks() {
        return Records.distinctResponses(receivedResponses, Response.OK, Request.UPDATE);
    }

    /** retransmissions included */
    public int getInviteOkCopies(long cseq) {
        int copies = 0;
        for (MessageRecord r : Records.responses(receivedResponses, Response.OK, Request.INVITE)) {
            if (r.cseq == cseq) {
                copies++;
            }
        }
        return copies;
    }

    public List<MessageRecord> getInvite422s() {
        return Records.responses(receivedResponses, Response.SESSION_INTERVAL_TOO_SMALL, Request.INVITE);
    }

    public List<MessageRecord> getUpdate422s() {
        return Records.responses(receivedResponses, Response.SESSION_INTERVAL_TOO_SMALL, Request.UPDATE);
    }

    public List<MessageRecord> getInvite408s() {
        return Records.responses(receivedResponses, Response.REQUEST_TIMEOUT, Request.INVITE);
    }

    public List<MessageRecord> getReceivedInvites() {
        return Records.requests(receivedRequests, Request.INVITE);
    }

    public List<MessageRecord> getReceivedUpdates() {
        return Records.requests(receivedRequests, Request.UPDATE);
    }

    public SessionTimerMode getMode() {
        return mode;
    }

    public TestAssertion getCompletedCallAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return inviteOkSeen && byeOkSeen;
            }
        };
    }

    public TestAssertion getInviteOkAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return inviteOkSeen;
            }
        };
    }

    public TestAssertion getByeOkAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return byeOkSeen;
            }
        };
    }

    public TestAssertion getSessionExpiredAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return isSessionExpiredSeen();
            }
        };
    }

    public TestAssertion getUpdateOkAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return updateOkSeen;
            }
        };
    }

    public TestAssertion getUpdate422Assertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return update422Seen;
            }
        };
    }

    public TestAssertion getStackRefreshesAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getStackRefreshes().size() == count;
            }
        };
    }

    public TestAssertion getAppRequestsAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getAppRequests().size() == count;
            }
        };
    }

    public TestAssertion getInviteOksAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getInviteOks().size() == count;
            }
        };
    }

    public TestAssertion getInviteOkCopiesAssertion(final long cseq, final int copies) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getInviteOkCopies(cseq) >= copies;
            }
        };
    }

    public TestAssertion getUpdateOksAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getUpdateOks().size() == count;
            }
        };
    }

    public TestAssertion getReceivedInvitesAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getReceivedInvites().size() == count;
            }
        };
    }

    public TestAssertion getReceivedUpdatesAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getReceivedUpdates().size() == count;
            }
        };
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("caller", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        Address contactAddress = addressFactory.createAddress(contactUri);
        return headerFactory.createContactHeader(contactAddress);
    }

    private ContentTypeHeader sdpContentType() throws Exception {
        return headerFactory.createContentTypeHeader("application", "sdp");
    }

    public void stop() {
        timer.cancel();
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
    
    public List<MessageRecord> getInvite487s() {
        return Records.responses(receivedResponses, Response.REQUEST_TERMINATED, Request.INVITE);
    }

    public List<MessageRecord> getSentCancels() {
        return Records.requests(sentRequests, Request.CANCEL);
    }

    /** the unanswered refresh was CANCELed at expiry and the UAS closed it with 487 */
    public TestAssertion getRefreshCancelledAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getSentCancels().size() == 1 && getInvite487s().size() == 1;
            }
        };
    }
}
