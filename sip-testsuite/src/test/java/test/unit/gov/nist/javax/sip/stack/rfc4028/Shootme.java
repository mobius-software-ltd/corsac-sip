package test.unit.gov.nist.javax.sip.stack.rfc4028;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.Dialog;
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
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.header.HeaderFactoryExt;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import gov.nist.javax.sip.message.MessageExt;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;
import test.unit.gov.nist.javax.sip.stack.rfc4028.Records.MessageRecord;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * MANUAL mode: puts the session timer headers on by hand and never arms a timer,
 * never refreshes, never BYEs.
 */
public class Shootme implements SipListenerExt {

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    static final String OK_ANSWER_SDP = "v=0\r\n" + "o=shootme 1000 1000 IN IP4 127.0.0.1\r\n" + "s=rfc4028-answer\r\n"
            + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 30000 RTP/AVP 0\r\n" + "a=rtpmap:0 PCMU/8000\r\n";

    static final String EARLY_OFFER_SDP = "v=0\r\n" + "o=shootme 2000 2000 IN IP4 127.0.0.1\r\n"
            + "s=rfc4028-183-offer\r\n" + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 30002 RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n";

    static final String REINVITE_ANSWER_SDP = "v=0\r\n" + "o=shootme 3000 3000 IN IP4 127.0.0.1\r\n"
            + "s=rfc4028-reinvite-answer\r\n" + "c=IN IP4 127.0.0.1\r\n" + "t=0 0\r\n" + "m=audio 30004 RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n";

    private final int port;
    private final SessionTimerMode mode;

    private final AddressFactory addressFactory;
    private final HeaderFactoryExt headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    /** "uac", "uas" or null; null means stack decides (STACK) or no Session-Expires at all (MANUAL). */
    public String refresher;
    public int sessionExpires = 90;
    /** Allow: UPDATE on our 2xx. */
    public boolean peerAllowsUpdate;
    public boolean sendReliableProvisional;
    public boolean sendRinging = true;
    public boolean dropRefresh;
    public int refreshResponse = Response.OK;
    /** MANUAL only: SE + Require: timer on the 2xx to a re-INVITE. */
    public boolean echoSeOnRefresh = true;
    public long reInviteAnswerDelay = 0;
    public long okDelay = 300;

    private volatile String toTag;
    private volatile ServerTransaction inviteStx;
    private volatile Request inviteRequest;
    private volatile Dialog dialog;
    private final Set<Long> inviteOksAcked = ConcurrentHashMap.newKeySet();

    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> sentRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<DialogTimeoutEvent.Reason> dialogTimeoutReasons = Collections
            .synchronizedList(new ArrayList<DialogTimeoutEvent.Reason>());

    private volatile boolean inviteSeen;
    private volatile boolean ackSeen;
    private volatile boolean byeSeen;
    private volatile boolean ioExceptionSeen;

    public Shootme(int port, SessionTimerMode mode) {
        this.port = port;
        this.mode = mode;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, true, false,
                false, mode.stackProperties());
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = (HeaderFactoryExt) protocolObjects.headerFactory;
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
        MessageRecord record = Records.record(request);
        receivedRequests.add(record);
        logger.info("shootme:" + port + " received " + record);
        try {
            if (method.equals(Request.ACK)) {
                ackSeen = true;
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            if (method.equals(Request.INVITE)) {
                ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
                if (to.getTag() == null) {
                    processInitialInvite(request, st);
                } else {
                    processReInvite(request, st);
                }
            } else if (method.equals(Request.PRACK)) {
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
                final ServerTransaction invite = inviteStx;
                final Request inviteReq = inviteRequest;
                schedule(new Runnable() {
                    @Override
                    public void run() {
                        sendInviteOk(inviteReq, invite);
                    }
                }, okDelay);
            } else if (method.equals(Request.UPDATE)) {
                Response ok = messageFactory.createResponse(Response.OK, request);
                ok.addHeader(createContact());
                if (mode == SessionTimerMode.MANUAL) {
                    copySessionExpires(request, ok);
                }
                st.sendResponse(ok);
            } else if (method.equals(Request.BYE)) {
                byeSeen = true;
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else if (method.equals(Request.CANCEL)) {
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else {
                st.sendResponse(messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, request));
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing " + method, ex);
        }
    }

    private void processInitialInvite(Request request, ServerTransaction st) throws Exception {
        inviteSeen = true;
        inviteStx = st;
        inviteRequest = request;
        dialog = st.getDialog();
        toTag = Integer.toHexString(new Random().nextInt());

        if (sendReliableProvisional) {
            Response progress = messageFactory.createResponse(Response.SESSION_PROGRESS, request);
            ((ToHeader) progress.getHeader(ToHeader.NAME)).setTag(toTag);
            progress.addHeader(createContact());
            progress.addHeader(headerFactory.createRequireHeader("100rel"));
            progress.setContent(EARLY_OFFER_SDP, sdpContentType());
            logger.info("shootme:" + port + " sending reliable 183 with offer");
            dialog.sendReliableProvisionalResponse(progress);
            // 2xx follows the PRACK
            return;
        }
        if (sendRinging) {
            Response ringing = messageFactory.createResponse(Response.RINGING, request);
            ((ToHeader) ringing.getHeader(ToHeader.NAME)).setTag(toTag);
            ringing.addHeader(createContact());
            st.sendResponse(ringing);
        }
        final ServerTransaction invite = st;
        final Request inviteReq = request;
        schedule(new Runnable() {
            @Override
            public void run() {
                sendInviteOk(inviteReq, invite);
            }
        }, okDelay);
    }

    private void sendInviteOk(Request request, ServerTransaction st) {
        try {
            Response ok = messageFactory.createResponse(Response.OK, request);
            ((ToHeader) ok.getHeader(ToHeader.NAME)).setTag(toTag);
            ok.addHeader(createContact());
            if (!sendReliableProvisional) {
                ok.setContent(OK_ANSWER_SDP, sdpContentType());
            }
            if (refresher != null) {
                SessionExpiresHeader se = headerFactory.createSessionExpiresHeader(sessionExpires);
                se.setRefresher(refresher);
                ok.setHeader(se);
                if (mode == SessionTimerMode.MANUAL) {
                    ok.addHeader(headerFactory.createRequireHeader("timer"));
                }
            }
            if (peerAllowsUpdate) {
                for (String m : new String[] { Request.INVITE, Request.ACK, Request.CANCEL, Request.BYE,
                        Request.UPDATE, Request.PRACK }) {
                    ok.addHeader(headerFactory.createAllowHeader(m));
                }
            }
            logger.info("shootme:" + port + " sending 200 to INVITE, refresher=" + refresher + " mode=" + mode);
            st.sendResponse(ok);
        } catch (Exception ex) {
            logger.error("shootme: could not send 200 to INVITE", ex);
        }
    }

    private void processReInvite(final Request request, final ServerTransaction st) {
        if (dropRefresh) {
            logger.info("shootme:" + port + " dropping re-INVITE on purpose");
            return;
        }
        Runnable answer = new Runnable() {
            @Override
            public void run() {
                try {
                    if (refreshResponse != Response.OK) {
                        logger.info("shootme:" + port + " answering re-INVITE with " + refreshResponse);
                        st.sendResponse(messageFactory.createResponse(refreshResponse, request));
                        return;
                    }
                    Response ok = messageFactory.createResponse(Response.OK, request);
                    ok.addHeader(createContact());
                    ok.setContent(REINVITE_ANSWER_SDP, sdpContentType());
                    if (mode == SessionTimerMode.MANUAL && echoSeOnRefresh) {
                        copySessionExpires(request, ok);
                    }
                    logger.info("shootme:" + port + " answering re-INVITE with 200, echoSe=" + echoSeOnRefresh);
                    st.sendResponse(ok);
                } catch (Exception ex) {
                    logger.error("shootme: could not answer re-INVITE", ex);
                }
            }
        };
        if (reInviteAnswerDelay > 0) {
            schedule(answer, reInviteAnswerDelay);
        } else {
            answer.run();
        }
    }

    /** by hand: copy SE, default refresher uac, Require: timer */
    private void copySessionExpires(Request request, Response response) throws Exception {
        SessionExpiresHeader requestSe = (SessionExpiresHeader) request.getHeader(SessionExpiresHeader.NAME);
        if (requestSe == null) {
            return;
        }
        SessionExpiresHeader se = headerFactory.createSessionExpiresHeader(requestSe.getExpires());
        se.setRefresher(requestSe.getRefresher() != null ? requestSe.getRefresher() : "uac");
        response.setHeader(se);
        response.addHeader(headerFactory.createRequireHeader("timer"));
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        boolean retransmission = responseEvent instanceof ResponseEventExt
                && ((ResponseEventExt) responseEvent).isRetransmission();
        MessageRecord record = Records.record(response, retransmission);
        receivedResponses.add(record);
        logger.info("shootme:" + port + " received " + record);
        try {
            if (response.getStatusCode() == Response.OK && Request.INVITE.equals(record.method)) {
                // 2xx to our own refresh re-INVITE, ACK once per CSeq
                if (inviteOksAcked.add(Long.valueOf(record.cseq))) {
                    Dialog d = responseEvent.getDialog();
                    d.sendAck(d.createAck(record.cseq));
                }
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing response " + response.getStatusCode(), ex);
        }
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
        if (messageSentEvent instanceof Request) {
            MessageRecord record = Records.record(messageSentEvent);
            sentRequests.add(record);
            logger.info("shootme:" + port + " sent " + record);
        }
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        logger.info("shootme:" + port + " dialog timeout " + timeoutEvent.getReason());
        dialogTimeoutReasons.add(timeoutEvent.getReason());
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
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

    public boolean isAckSeen() {
        return ackSeen;
    }

    public boolean isByeSeen() {
        return byeSeen;
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public boolean isSessionExpiredSeen() {
        return dialogTimeoutReasons.contains(DialogTimeoutEvent.Reason.SessionExpired);
    }

    public List<MessageRecord> getReceivedResponses() {
        return receivedResponses;
    }

    public List<MessageRecord> getReceivedInvites() {
        return Records.requests(receivedRequests, Request.INVITE);
    }

    public List<MessageRecord> getReceivedUpdates() {
        return Records.requests(receivedRequests, Request.UPDATE);
    }

    public List<MessageRecord> getInviteOks() {
        return Records.distinctResponses(receivedResponses, Response.OK, Request.INVITE);
    }

    public List<MessageRecord> getUpdateOks() {
        return Records.distinctResponses(receivedResponses, Response.OK, Request.UPDATE);
    }

    /** every INVITE/UPDATE this stack sent; nothing but the stack refreshes ever does */
    public List<MessageRecord> getStackRefreshes() {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (sentRequests) {
            for (MessageRecord r : sentRequests) {
                if (r.isRequest() && (Request.INVITE.equals(r.method) || Request.UPDATE.equals(r.method))) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    public List<MessageRecord> getSentByes() {
        return Records.requests(sentRequests, Request.BYE);
    }

    public SessionTimerMode getMode() {
        return mode;
    }

    public int getPort() {
        return port;
    }

    public TestAssertion getCompletedCallAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return inviteSeen && ackSeen && byeSeen;
            }
        };
    }

    public TestAssertion getByeAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return byeSeen;
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

    public TestAssertion getInviteOksAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getInviteOks().size() == count;
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

    private void schedule(final Runnable task, long delay) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.error("shootme: scheduled task failed", t);
                }
            }
        }, delay);
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("callee", myAddress);
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
}
