package test.unit.gov.nist.javax.sip.stack.b2b;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ClientTransaction;
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
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.SubscriptionStateHeader;
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
import gov.nist.javax.sip.message.MessageExt;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;
import test.unit.gov.nist.javax.sip.stack.b2b.Records.MessageRecord;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 */
public class Shootme implements SipListenerExt {

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

    public boolean sendSessionProgress = true;
    public boolean sendReliableProvisional;
    /** 183 that long after the INVITE. */
    public long progressDelay = 0;
    /**
     * Final response that long after the 183, after the PRACK when it is reliable; 200 to
     * the SUBSCRIBE that long after an early NOTIFY.
     */
    public long okDelay = 300;
    /** Final response to the INVITE, 0 = never answer. */
    public int inviteResponse = Response.OK;
    /** -1 = never. BYE after the ACK, after the re-INVITE when there is one. */
    public long byeDelay = -1;
    /** -1 = never. re-INVITE after the ACK. */
    public long reInviteDelay = -1;
    public int subscribeResponse = Response.OK;
    /** NOTIFY before the 200 to the SUBSCRIBE, NOTIFY terminated once it is answered. */
    public boolean earlyNotify;

    private volatile String toTag;
    private volatile ServerTransaction inviteStx;
    private volatile Request inviteRequest;
    private volatile boolean inviteAnswered;
    private volatile ServerTransaction subscribeStx;
    private volatile Request subscribeRequest;
    private volatile boolean subscribeAnswered;
    /** CSeq of the NOTIFY terminated we sent, 0 = none yet */
    private volatile long terminatedNotifyCseq;
    /** NOTIFY once the 200 to the SUBSCRIBE went out, the dialog cannot create it before */
    private volatile boolean notifyOnSubscribeOk;
    private volatile long lastNotifyCseq;
    private volatile Dialog dialog;
    private final Set<Long> inviteOksAcked = ConcurrentHashMap.newKeySet();

    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> sentRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<DialogTimeoutEvent.Reason> dialogTimeoutReasons = Collections
            .synchronizedList(new ArrayList<DialogTimeoutEvent.Reason>());

    private volatile int acksSeen;
    private volatile boolean byeSeen;
    private volatile boolean cancelSeen;
    private volatile boolean byeOkSeen;
    private volatile boolean reInviteFinalSeen;
    private volatile boolean ioExceptionSeen;
    private volatile int timeoutEvents;

    public Shootme(int port, String transport) {
        this.port = port;
        this.transport = transport;
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
        MessageRecord record = Records.record(request);
        receivedRequests.add(record);
        logger.info("shootme:" + port + " received " + record);
        try {
            if (method.equals(Request.ACK)) {
                if (++acksSeen == 1) {
                    afterAck();
                }
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            if (method.equals(Request.INVITE)) {
                st.sendResponse(messageFactory.createResponse(Response.TRYING, request));
            }
            boolean initial = ((ToHeader) request.getHeader(ToHeader.NAME)).getTag() == null;
            if (method.equals(Request.INVITE)) {
                if (initial) {
                    processInitialInvite(request, st);
                } else {
                    Response ok = messageFactory.createResponse(Response.OK, request);
                    ok.addHeader(createContact());
                    st.sendResponse(ok);
                }
            } else if (method.equals(Request.PRACK)) {
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
                scheduleInviteFinal();
            } else if (method.equals(Request.UPDATE)) {
                Response ok = messageFactory.createResponse(Response.OK, request);
                ok.addHeader(createContact());
                st.sendResponse(ok);
            } else if (method.equals(Request.BYE)) {
                byeSeen = true;
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else if (method.equals(Request.CANCEL)) {
                cancelSeen = true;
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
                sendInviteFinal(Response.REQUEST_TERMINATED);
            } else if (method.equals(Request.SUBSCRIBE)) {
                if (initial) {
                    processSubscribe(request, st);
                } else {
                    // un-SUBSCRIBE
                    Response ok = messageFactory.createResponse(Response.OK, request);
                    ok.addHeader(createContact());
                    ok.addHeader(headerFactory.createExpiresHeader(0));
                    st.sendResponse(ok);
                    sendNotify(SubscriptionStateHeader.TERMINATED);
                }
            } else {
                st.sendResponse(messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, request));
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing " + method, ex);
        }
    }

    private void processInitialInvite(Request request, ServerTransaction st) throws Exception {
        inviteStx = st;
        inviteRequest = request;
        dialog = st.getDialog();
        toTag = Integer.toHexString(new Random().nextInt());
        if (!sendSessionProgress && !sendReliableProvisional) {
            scheduleInviteFinal();
            return;
        }
        Runnable progress = new Runnable() {
            @Override
            public void run() {
                sendSessionProgress();
            }
        };
        if (progressDelay > 0) {
            schedule(progress, progressDelay);
        } else {
            progress.run();
        }
    }

    private void sendSessionProgress() {
        try {
            Response progress = createInviteResponse(Response.SESSION_PROGRESS);
            if (sendReliableProvisional) {
                progress.addHeader(headerFactory.createRequireHeader("100rel"));
                logger.info("shootme:" + port + " sending reliable 183");
                dialog.sendReliableProvisionalResponse(progress);
                return;
            }
            inviteStx.sendResponse(progress);
            scheduleInviteFinal();
        } catch (Exception ex) {
            logger.error("shootme: could not send 183", ex);
        }
    }

    private void scheduleInviteFinal() {
        if (inviteResponse == 0) {
            return;
        }
        schedule(new Runnable() {
            @Override
            public void run() {
                sendInviteFinal(inviteResponse);
            }
        }, okDelay);
    }

    private synchronized void sendInviteFinal(int status) {
        if (inviteStx == null || inviteAnswered) {
            return;
        }
        inviteAnswered = true;
        try {
            logger.info("shootme:" + port + " sending " + status + " to INVITE");
            inviteStx.sendResponse(createInviteResponse(status));
        } catch (Exception ex) {
            logger.error("shootme: could not answer INVITE", ex);
        }
    }

    private Response createInviteResponse(int status) throws Exception {
        Response response = messageFactory.createResponse(status, inviteRequest);
        ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
        if (status < 300) {
            response.addHeader(createContact());
        }
        return response;
    }

    private void afterAck() {
        if (reInviteDelay >= 0) {
            schedule(new Runnable() {
                @Override
                public void run() {
                    sendReInvite();
                }
            }, reInviteDelay);
        } else {
            scheduleBye();
        }
    }

    private void scheduleBye() {
        if (byeDelay < 0) {
            return;
        }
        schedule(new Runnable() {
            @Override
            public void run() {
                sendBye();
            }
        }, byeDelay);
    }

    private void sendReInvite() {
        try {
            Request reInvite = dialog.createRequest(Request.INVITE);
            reInvite.setHeader(createContact());
            ClientTransaction ct = sipProvider.getNewClientTransaction(reInvite);
            logger.info("shootme:" + port + " sending re-INVITE");
            dialog.sendRequest(ct);
        } catch (Exception ex) {
            logger.error("shootme: could not send re-INVITE", ex);
        }
    }

    public void sendBye() {
        try {
            ClientTransaction ct = sipProvider.getNewClientTransaction(dialog.createRequest(Request.BYE));
            logger.info("shootme:" + port + " sending BYE");
            dialog.sendRequest(ct);
        } catch (Exception ex) {
            logger.error("shootme: could not send BYE", ex);
        }
    }

    private void processSubscribe(Request request, ServerTransaction st) throws Exception {
        subscribeStx = st;
        subscribeRequest = request;
        dialog = st.getDialog();
        toTag = Integer.toHexString(new Random().nextInt());
        if (subscribeResponse != Response.OK) {
            Response response = messageFactory.createResponse(subscribeResponse, request);
            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
            st.sendResponse(response);
            return;
        }
        if (earlyNotify) {
            sendEarlyNotify(1, SubscriptionStateHeader.ACTIVE);
            schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        sendSubscribeOk();
                    } catch (Exception ex) {
                        logger.error("shootme: could not send 200 to SUBSCRIBE", ex);
                    }
                }
            }, okDelay);
            return;
        }
        notifyOnSubscribeOk = true;
        sendSubscribeOk();
    }

    private synchronized void sendSubscribeOk() throws Exception {
        if (subscribeAnswered) {
            return;
        }
        subscribeAnswered = true;
        Response ok = messageFactory.createResponse(Response.OK, subscribeRequest);
        ((ToHeader) ok.getHeader(ToHeader.NAME)).setTag(toTag);
        ok.addHeader(createContact());
        ok.addHeader(headerFactory.createExpiresHeader(Shootist.SUBSCRIBE_EXPIRES));
        logger.info("shootme:" + port + " sending 200 to SUBSCRIBE");
        subscribeStx.sendResponse(ok);
    }

    private void sendNotify(String state) throws Exception {
        Request notify = dialog.createRequest(Request.NOTIFY);
        CSeqHeader cseq = (CSeqHeader) notify.getHeader(CSeqHeader.NAME);
        if (cseq.getSeqNumber() <= lastNotifyCseq) {
            cseq.setSeqNumber(lastNotifyCseq + 1);
        }
        lastNotifyCseq = cseq.getSeqNumber();
        if (SubscriptionStateHeader.TERMINATED.equals(state)) {
            terminatedNotifyCseq = cseq.getSeqNumber();
        }
        notify.setHeader(headerFactory.createEventHeader(Shootist.EVENT));
        notify.setHeader(headerFactory.createSubscriptionStateHeader(state));
        notify.setHeader(createContact());
        ClientTransaction ct = sipProvider.getNewClientTransaction(notify);
        logger.info("shootme:" + port + " sending NOTIFY " + state);
        dialog.sendRequest(ct);
    }

    /** by hand, the dialog cannot create requests before the 200 to the SUBSCRIBE */
    private void sendEarlyNotify(long cseq, String state) throws Exception {
        URI requestUri = (URI) ((ContactHeader) subscribeRequest.getHeader(ContactHeader.NAME)).getAddress().getURI()
                .clone();
        FromHeader subscriber = (FromHeader) subscribeRequest.getHeader(FromHeader.NAME);
        ToHeader notifier = (ToHeader) subscribeRequest.getHeader(ToHeader.NAME);
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));
        lastNotifyCseq = cseq;
        Request notify = messageFactory.createRequest(requestUri, Request.NOTIFY,
                (CallIdHeader) subscribeRequest.getHeader(CallIdHeader.NAME).clone(),
                headerFactory.createCSeqHeader(cseq, Request.NOTIFY),
                headerFactory.createFromHeader((Address) notifier.getAddress().clone(), toTag),
                headerFactory.createToHeader((Address) subscriber.getAddress().clone(), subscriber.getTag()), viaHeaders,
                headerFactory.createMaxForwardsHeader(70));
        notify.setHeader(headerFactory.createEventHeader(Shootist.EVENT));
        notify.setHeader(headerFactory.createSubscriptionStateHeader(state));
        notify.setHeader(createContact());
        ClientTransaction ct = sipProvider.getNewClientTransaction(notify);
        logger.info("shootme:" + port + " sending early NOTIFY " + state);
        ct.sendRequest();
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        boolean retransmission = responseEvent instanceof ResponseEventExt
                && ((ResponseEventExt) responseEvent).isRetransmission();
        MessageRecord record = Records.record(response, retransmission);
        receivedResponses.add(record);
        int status = response.getStatusCode();
        logger.info("shootme:" + port + " received " + record);
        try {
            if (Request.NOTIFY.equals(record.method)) {
                if (status == Response.OK && record.cseq == terminatedNotifyCseq) {
                    dialog.delete();
                } else if (status == Response.OK && earlyNotify && terminatedNotifyCseq == 0) {
                    sendNotify(SubscriptionStateHeader.TERMINATED);
                }
            } else if (Request.INVITE.equals(record.method) && status >= 200) {
                // final response to our re-INVITE, ACK the 2xx once per CSeq
                if (status < 300 && inviteOksAcked.add(Long.valueOf(record.cseq))) {
                    Dialog d = responseEvent.getDialog();
                    d.sendAck(d.createAck(record.cseq));
                }
                if (!retransmission && !reInviteFinalSeen) {
                    reInviteFinalSeen = true;
                    scheduleBye();
                }
            } else if (Request.BYE.equals(record.method) && status == Response.OK) {
                byeOkSeen = true;
            }
        } catch (Exception ex) {
            logger.error("shootme: unexpected exception processing response " + status, ex);
        }
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
        MessageRecord record = Records.record(messageSentEvent);
        if (messageSentEvent instanceof Request) {
            sentRequests.add(record);
            logger.info("shootme:" + port + " sent " + record);
        } else if (notifyOnSubscribeOk && record.status == Response.OK && Request.SUBSCRIBE.equals(record.method)) {
            notifyOnSubscribeOk = false;
            try {
                sendNotify(SubscriptionStateHeader.ACTIVE);
            } catch (Exception ex) {
                logger.error("shootme: could not send NOTIFY", ex);
            }
        }
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        logger.info("shootme:" + port + " dialog timeout " + timeoutEvent.getReason());
        dialogTimeoutReasons.add(timeoutEvent.getReason());
    }

    public void processTimeout(TimeoutEvent e) {
        timeoutEvents++;
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

    public boolean isByeSeen() {
        return byeSeen;
    }

    public boolean isCancelSeen() {
        return cancelSeen;
    }

    public boolean isByeOkSeen() {
        return byeOkSeen;
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public int getTimeoutEvents() {
        return timeoutEvents;
    }

    public List<DialogTimeoutEvent.Reason> getDialogTimeoutReasons() {
        return dialogTimeoutReasons;
    }

    public List<MessageRecord> getReceivedRequests() {
        return receivedRequests;
    }

    public List<MessageRecord> getReceivedResponses() {
        return receivedResponses;
    }

    public List<MessageRecord> getSentRequests() {
        return sentRequests;
    }

    /** the INVITE or SUBSCRIBE that opened the dialog, as it came in */
    public MessageRecord getFirstReceivedRequest() {
        synchronized (receivedRequests) {
            return receivedRequests.isEmpty() ? null : receivedRequests.get(0);
        }
    }

    public int getPort() {
        return port;
    }

    public TestAssertion getByeAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return byeSeen;
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

    public TestAssertion getReceivedRequestsAssertion(final String method, final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return Records.requests(receivedRequests, method).size() == count;
            }
        };
    }

    public TestAssertion getResponseAssertion(final int status, final String method) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !Records.responses(receivedResponses, status, method).isEmpty();
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

    public int getDialogCount() {
        return ((SipStackImpl) sipStack).getDialogs().size();
    }

    public TestAssertion getNoTransactionsAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getClientTransactionTableSize() == 0 && getServerTransactionTableSize() == 0;
            }
        };
    }

    public TestAssertion getNoDialogsAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getDialogCount() == 0;
            }
        };
    }
}
