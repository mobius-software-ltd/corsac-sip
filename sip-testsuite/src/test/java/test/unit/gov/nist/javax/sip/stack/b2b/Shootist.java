package test.unit.gov.nist.javax.sip.stack.b2b;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
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
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
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
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 */
public class Shootist implements SipListenerExt {

    private static final Logger logger = LogManager.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    static final String EVENT = "presence";
    static final int SUBSCRIBE_EXPIRES = 3600;

    private final int port;
    private final int peerPort;
    private final String transport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    /** Early UPDATE once the 183 arrives. */
    public boolean sendUpdate;
    /** CANCEL once the 183 arrives. */
    public boolean cancelOnProvisional;
    /** Never ACK the 200. */
    public boolean dropAck;
    /** -1 = never. BYE after the ACK. */
    public long byeDelay = -1;
    /** -1 = never. re-INVITE after the ACK. */
    public long reInviteDelay = -1;
    /** Final response to a re-INVITE from the far end. */
    public int reInviteResponse = Response.OK;
    /** -1 = never. un-SUBSCRIBE after the 200 to the first NOTIFY. */
    public long unsubscribeDelay = -1;

    private volatile Dialog dialog;
    private volatile ClientTransaction inviteTransaction;
    private final Set<Long> inviteOksHandled = ConcurrentHashMap.newKeySet();
    private volatile boolean updateSent;
    private volatile boolean cancelSent;
    private volatile boolean unsubscribeScheduled;
    /** NOTIFY that came before the 200 to the SUBSCRIBE, answered once that 200 is in */
    private volatile ServerTransaction pendingNotify;
    private volatile Request pendingNotifyRequest;

    private final List<MessageRecord> sentRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<DialogTimeoutEvent.Reason> dialogTimeoutReasons = Collections
            .synchronizedList(new ArrayList<DialogTimeoutEvent.Reason>());

    private volatile boolean inviteOkSeen;
    private volatile boolean reInviteOkSeen;
    private volatile boolean subscribeOkSeen;
    private volatile boolean notifyBeforeSubscribeOk;
    private volatile boolean notifyTerminatedSeen;
    private volatile boolean byeOkSeen;
    private volatile boolean byeReceived;
    private volatile boolean ioExceptionSeen;
    private volatile int timeoutEvents;

    public Shootist(int port, int peerPort, String transport, Properties properties) {
        this.port = port;
        this.peerPort = peerPort;
        this.transport = transport;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, true, false,
                false, properties);
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
            Request invite = createRequest(Request.INVITE);
            inviteTransaction = sipProvider.getNewClientTransaction(invite);
            logger.info("shootist:" + port + " sending INVITE");
            inviteTransaction.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send INVITE", ex);
        }
    }

    public void sendSubscribe() {
        try {
            Request subscribe = createRequest(Request.SUBSCRIBE);
            subscribe.addHeader(headerFactory.createEventHeader(EVENT));
            subscribe.addHeader(headerFactory.createExpiresHeader(SUBSCRIBE_EXPIRES));
            ClientTransaction ct = sipProvider.getNewClientTransaction(subscribe);
            logger.info("shootist:" + port + " sending SUBSCRIBE");
            ct.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send SUBSCRIBE", ex);
        }
    }

    private Request createRequest(String method) throws Exception {
        SipURI fromUri = addressFactory.createSipURI("caller", "b2b.test");
        String fromTag = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
        FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri), fromTag);
        SipURI toUri = addressFactory.createSipURI("callee", "b2b.test");
        ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

        SipURI requestUri = addressFactory.createSipURI("callee", myAddress);
        requestUri.setPort(peerPort);
        requestUri.setTransportParam(transport);

        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));
        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, method);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        Request request = messageFactory.createRequest(requestUri, method, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);
        request.addHeader(createContact());
        return request;
    }

    private void sendInDialog(Dialog d, Request request) throws Exception {
        ClientTransaction ct = sipProvider.getNewClientTransaction(request);
        logger.info("shootist:" + port + " sending " + request.getMethod());
        d.sendRequest(ct);
    }

    private void sendEarlyUpdate(Dialog earlyDialog) {
        try {
            Request update = earlyDialog.createRequest(Request.UPDATE);
            update.setHeader(createContact());
            sendInDialog(earlyDialog, update);
        } catch (Exception ex) {
            logger.error("shootist: could not send early UPDATE", ex);
        }
    }

    private void sendReInvite() {
        try {
            Request reInvite = dialog.createRequest(Request.INVITE);
            reInvite.setHeader(createContact());
            sendInDialog(dialog, reInvite);
        } catch (Exception ex) {
            logger.error("shootist: could not send re-INVITE", ex);
        }
    }

    private void sendUnsubscribe() {
        try {
            Request unsubscribe = dialog.createRequest(Request.SUBSCRIBE);
            unsubscribe.setHeader(headerFactory.createEventHeader(EVENT));
            unsubscribe.setHeader(headerFactory.createExpiresHeader(0));
            unsubscribe.setHeader(createContact());
            sendInDialog(dialog, unsubscribe);
        } catch (Exception ex) {
            logger.error("shootist: could not send un-SUBSCRIBE", ex);
        }
    }

    public void sendBye() {
        try {
            sendInDialog(dialog, dialog.createRequest(Request.BYE));
        } catch (Exception ex) {
            logger.error("shootist: could not send BYE", ex);
        }
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
            if (Request.INVITE.equals(method)) {
                if (status / 100 == 1) {
                    if (cancelOnProvisional && !cancelSent) {
                        cancelSent = true;
                        Request cancel = inviteTransaction.createCancel();
                        sipProvider.getNewClientTransaction(cancel).sendRequest();
                    }
                    if (sendUpdate && !updateSent && record.toTag != null) {
                        updateSent = true;
                        sendEarlyUpdate(responseEvent.getDialog());
                    }
                } else if (status / 100 == 2) {
                    processInviteOk(responseEvent, record);
                }
            } else if (Request.SUBSCRIBE.equals(method)) {
                if (status / 100 == 2 && !subscribeOkSeen) {
                    subscribeOkSeen = true;
                    dialog = responseEvent.getDialog();
                    if (pendingNotify != null) {
                        pendingNotify.sendResponse(messageFactory.createResponse(Response.OK, pendingNotifyRequest));
                        pendingNotify = null;
                        afterNotifyAnswered(pendingNotifyRequest);
                    }
                }
            } else if (Request.BYE.equals(method) && status == Response.OK) {
                byeOkSeen = true;
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception processing response " + status, ex);
        }
    }

    private void processInviteOk(ResponseEvent responseEvent, MessageRecord record) throws Exception {
        final Dialog okDialog = responseEvent.getDialog();
        if (!inviteOksHandled.add(Long.valueOf(record.cseq))) {
            logger.info("shootist:" + port + " retransmitted 200 for INVITE cseq=" + record.cseq + ", ignoring");
            return;
        }
        if (inviteOkSeen) {
            reInviteOkSeen = true;
            okDialog.sendAck(okDialog.createAck(record.cseq));
            return;
        }
        inviteOkSeen = true;
        dialog = okDialog;
        if (dropAck) {
            return;
        }
        okDialog.sendAck(okDialog.createAck(record.cseq));
        if (reInviteDelay >= 0) {
            schedule(new Runnable() {
                @Override
                public void run() {
                    sendReInvite();
                }
            }, reInviteDelay);
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
            if (method.equals(Request.INVITE)) {
                st.sendResponse(messageFactory.createResponse(Response.TRYING, request));
            }
            if (method.equals(Request.BYE)) {
                byeReceived = true;
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else if (method.equals(Request.INVITE)) {
                Response response = messageFactory.createResponse(reInviteResponse, request);
                if (reInviteResponse == Response.OK) {
                    response.addHeader(createContact());
                }
                st.sendResponse(response);
            } else if (method.equals(Request.NOTIFY)) {
                if (!subscribeOkSeen) {
                    notifyBeforeSubscribeOk = true;
                    pendingNotify = st;
                    pendingNotifyRequest = request;
                    return;
                }
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
                afterNotifyAnswered(request);
            } else {
                st.sendResponse(messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, request));
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception processing " + method, ex);
        }
    }

    private void afterNotifyAnswered(Request notify) {
        SubscriptionStateHeader state = (SubscriptionStateHeader) notify.getHeader(SubscriptionStateHeader.NAME);
        if (state != null && SubscriptionStateHeader.TERMINATED.equalsIgnoreCase(state.getState())) {
            dialog.delete();
            notifyTerminatedSeen = true;
        } else if (unsubscribeDelay >= 0 && !unsubscribeScheduled) {
            unsubscribeScheduled = true;
            schedule(new Runnable() {
                @Override
                public void run() {
                    sendUnsubscribe();
                }
            }, unsubscribeDelay);
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
        logger.info("shootist:" + port + " dialog timeout " + timeoutEvent.getReason());
        dialogTimeoutReasons.add(timeoutEvent.getReason());
        if (timeoutEvent.getReason() == DialogTimeoutEvent.Reason.EarlyStateTimeout) {
            timeoutEvent.getDialog().delete();
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

    public boolean isReInviteOkSeen() {
        return reInviteOkSeen;
    }

    public boolean isSubscribeOkSeen() {
        return subscribeOkSeen;
    }

    public boolean isNotifyBeforeSubscribeOk() {
        return notifyBeforeSubscribeOk;
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

    public int getTimeoutEvents() {
        return timeoutEvents;
    }

    public List<DialogTimeoutEvent.Reason> getDialogTimeoutReasons() {
        return dialogTimeoutReasons;
    }

    public List<MessageRecord> getReceivedResponses() {
        return receivedResponses;
    }

    public List<MessageRecord> getReceivedRequests() {
        return receivedRequests;
    }

    public List<MessageRecord> getSentRequests() {
        return sentRequests;
    }

    /** the INVITE or SUBSCRIBE that opened the dialog, as it went out */
    public MessageRecord getFirstSentRequest() {
        synchronized (sentRequests) {
            return sentRequests.isEmpty() ? null : sentRequests.get(0);
        }
    }

    public List<MessageRecord> getResponses(int status, String method) {
        return Records.responses(receivedResponses, status, method);
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

    public TestAssertion getByeReceivedAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return byeReceived;
            }
        };
    }

    public TestAssertion getNotifyTerminatedAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return notifyTerminatedSeen;
            }
        };
    }

    public TestAssertion getResponseAssertion(final int status, final String method) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !getResponses(status, method).isEmpty();
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
