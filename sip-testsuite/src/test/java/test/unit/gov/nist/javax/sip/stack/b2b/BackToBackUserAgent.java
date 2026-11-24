package test.unit.gov.nist.javax.sip.stack.b2b;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RSeqHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
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
 * A very simple B2BUA for one call or subscription: leg A faces the shootist, leg B
 * the shootme(s), each leg with its own Call-ID and tags. Every INVITE gets 100 first,
 * BYE and CANCEL are answered hop by hop, everything else end to end. More than one
 * target forks leg B.
 */
public class BackToBackUserAgent implements SipListenerExt {

    private static final Logger logger = LogManager.getLogger(BackToBackUserAgent.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int peerPort;
    private final int[] targetPorts;
    private final String transport;
    private final boolean automaticDialogSupport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider legAProvider;
    private SipProvider legBProvider;

    private volatile ServerTransaction legAStx;
    private volatile Request legARequest;
    private volatile String legATag;
    private volatile Dialog legADialog;
    private volatile Dialog legBDialog;
    private final List<ClientTransaction> forks = new CopyOnWriteArrayList<ClientTransaction>();
    private final Set<ClientTransaction> finishedForks = ConcurrentHashMap.newKeySet();
    /** leg A dialog each fork's provisional went out on */
    private final Map<ClientTransaction, Dialog> forkDialogs = new ConcurrentHashMap<ClientTransaction, Dialog>();
    private volatile ClientTransaction primaryFork;
    private volatile boolean answered;
    private volatile boolean finalSent;
    private volatile boolean released;
    /** the final response to the initial request is on the wire, the leg A dialog can create requests */
    private volatile boolean legAFinalOut;
    /** NOTIFY from leg B that came while the leg A final response was still queued */
    private ServerTransaction pendingNotifyStx;
    private Request pendingNotify;
    private long legANotifyCseq;

    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private final List<DialogTimeoutEvent.Reason> dialogTimeoutReasons = Collections
            .synchronizedList(new ArrayList<DialogTimeoutEvent.Reason>());
    private volatile int forkedDialogsTerminated;
    private volatile boolean ioExceptionSeen;
    private volatile int timeoutEvents;

    public BackToBackUserAgent(int port, int peerPort, String transport, boolean automaticDialogSupport,
            Properties properties, int... targetPorts) {
        this.port = port;
        this.peerPort = peerPort;
        this.targetPorts = targetPorts;
        this.transport = transport;
        this.automaticDialogSupport = automaticDialogSupport;
        ProtocolObjects protocolObjects = new ProtocolObjects("b2b-" + port, "gov.nist", transport,
                automaticDialogSupport, true, false, properties);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint legAListeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            ListeningPoint legBListeningPoint = sipStack.createListeningPoint(myAddress, peerPort, transport);
            this.legAProvider = sipStack.createSipProvider(legAListeningPoint);
            this.legBProvider = sipStack.createSipProvider(legBListeningPoint);
            this.legAProvider.addSipListener(this);
            this.legBProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create b2bua", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        boolean fromLegA = requestEvent.getSource() == legAProvider;
        MessageRecord record = Records.record(request);
        receivedRequests.add(record);
        logger.info("b2b:" + port + " received " + record);
        try {
            if (method.equals(Request.ACK)) {
                // leg B was ACKed when its 2xx came in
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            if (method.equals(Request.INVITE)) {
                st.sendResponse(messageFactory.createResponse(Response.TRYING, request));
            }
            if (method.equals(Request.CANCEL)) {
                processCancel(st, request);
            } else if (((ToHeader) request.getHeader(ToHeader.NAME)).getTag() == null) {
                processInitialRequest(st, request);
            } else {
                processInDialogRequest(fromLegA, st, request);
            }
        } catch (Exception ex) {
            logger.error("b2b: unexpected exception processing " + method, ex);
        }
    }

    private void processInitialRequest(ServerTransaction st, Request request) throws Exception {
        if (!automaticDialogSupport) {
            legAProvider.getNewDialog(st);
        }
        legAStx = st;
        legARequest = request;
        legADialog = st.getDialog();
        legATag = Integer.toHexString(new Random().nextInt());
        CallIdHeader callId = legBProvider.getNewCallId();
        String fromTag = Integer.toHexString(new Random().nextInt());
        for (int targetPort : targetPorts) {
            ClientTransaction ct = legBProvider
                    .getNewClientTransaction(createLegBRequest(request, targetPort, callId, fromTag));
            if (!automaticDialogSupport) {
                legBProvider.getNewDialog(ct);
            }
            forks.add(ct);
        }
        for (ClientTransaction ct : forks) {
            logger.info("b2b:" + port + " forwarding " + request.getMethod() + " to " + ct.getRequest().getRequestURI());
            ct.sendRequest();
        }
    }

    private Request createLegBRequest(Request request, int targetPort, CallIdHeader callId, String fromTag)
            throws Exception {
        String method = request.getMethod();
        SipURI requestUri = addressFactory.createSipURI(((SipURI) request.getRequestURI()).getUser(), myAddress);
        requestUri.setPort(targetPort);
        requestUri.setTransportParam(transport);
        Address from = (Address) ((FromHeader) request.getHeader(FromHeader.NAME)).getAddress().clone();
        Address to = (Address) ((ToHeader) request.getHeader(ToHeader.NAME)).getAddress().clone();
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, peerPort, transport, null));
        Request out = messageFactory.createRequest(requestUri, method, (CallIdHeader) callId.clone(),
                headerFactory.createCSeqHeader(1L, method), headerFactory.createFromHeader(from, fromTag),
                headerFactory.createToHeader(to, null), viaHeaders, headerFactory.createMaxForwardsHeader(70));
        out.addHeader(createContact(peerPort));
        copyHeader(request, out, EventHeader.NAME);
        copyHeader(request, out, ExpiresHeader.NAME);
        if (method.equals(Request.INVITE)) {
            out.addHeader(headerFactory.createSupportedHeader("100rel"));
        }
        return out;
    }

    private void processCancel(ServerTransaction st, Request request) throws Exception {
        st.sendResponse(messageFactory.createResponse(Response.OK, request));
        sendFinalUpstream(Response.REQUEST_TERMINATED, null);
        released = true;
        cancelPendingForks();
    }

    private void processInDialogRequest(boolean fromLegA, ServerTransaction st, Request request) throws Exception {
        String method = request.getMethod();
        SipProvider peerProvider = fromLegA ? legBProvider : legAProvider;
        Dialog peer = fromLegA ? legBDialog : legADialog;
        boolean byHand = !fromLegA && method.equals(Request.NOTIFY) && !finalSent;
        if (released || legAStx == null || (peer == null && !byHand)) {
            st.sendResponse(messageFactory.createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, request));
            return;
        }
        if (!fromLegA && method.equals(Request.NOTIFY) && finalSent && holdNotify(st, request)) {
            return;
        }
        if (method.equals(Request.BYE)) {
            st.sendResponse(messageFactory.createResponse(Response.OK, request));
            released = true;
            sendBye(peerProvider, peer);
            return;
        }
        Request out;
        if (byHand) {
            out = createLegANotify();
        } else {
            out = peer.createRequest(method);
            CSeqHeader cseq = (CSeqHeader) out.getHeader(CSeqHeader.NAME);
            if (!fromLegA && cseq.getSeqNumber() <= legANotifyCseq) {
                // above the NOTIFYs built by hand
                cseq.setSeqNumber(legANotifyCseq + 1);
            }
        }
        copyHeader(request, out, EventHeader.NAME);
        copyHeader(request, out, ExpiresHeader.NAME);
        copyHeader(request, out, SubscriptionStateHeader.NAME);
        if (isTargetRefresh(method)) {
            out.setHeader(createContact(fromLegA ? peerPort : port));
        }
        ClientTransaction ct = peerProvider.getNewClientTransaction(out);
        ct.setApplicationData(st);
        logger.info("b2b:" + port + " relaying " + method + (fromLegA ? " to leg B" : " to leg A"));
        if (byHand) {
            ct.sendRequest();
        } else {
            peer.sendRequest(ct);
        }
    }

    private synchronized boolean holdNotify(ServerTransaction st, Request request) {
        if (legAFinalOut) {
            return false;
        }
        pendingNotifyStx = st;
        pendingNotify = request;
        return true;
    }

    /** NOTIFY before the 200 to the SUBSCRIBE went out on leg A: the dialog cannot create it yet */
    private synchronized Request createLegANotify() throws Exception {
        URI requestUri = (URI) ((ContactHeader) legARequest.getHeader(ContactHeader.NAME)).getAddress().getURI()
                .clone();
        FromHeader subscriber = (FromHeader) legARequest.getHeader(FromHeader.NAME);
        ToHeader notifier = (ToHeader) legARequest.getHeader(ToHeader.NAME);
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));
        return messageFactory.createRequest(requestUri, Request.NOTIFY,
                (CallIdHeader) legARequest.getHeader(CallIdHeader.NAME).clone(),
                headerFactory.createCSeqHeader(++legANotifyCseq, Request.NOTIFY),
                headerFactory.createFromHeader((Address) notifier.getAddress().clone(), legATag),
                headerFactory.createToHeader((Address) subscriber.getAddress().clone(), subscriber.getTag()),
                viaHeaders, headerFactory.createMaxForwardsHeader(70));
    }

    private void sendBye(SipProvider provider, Dialog dialog) throws Exception {
        if (dialog == null || dialog.getState() == DialogState.TERMINATED) {
            return;
        }
        ClientTransaction ct = provider.getNewClientTransaction(dialog.createRequest(Request.BYE));
        logger.info("b2b:" + port + " sending BYE");
        dialog.sendRequest(ct);
    }

    private void cancelPendingForks() throws Exception {
        for (ClientTransaction ct : forks) {
            if (!finishedForks.contains(ct)) {
                logger.info("b2b:" + port + " sending CANCEL to " + ct.getRequest().getRequestURI());
                legBProvider.getNewClientTransaction(ct.createCancel()).sendRequest();
            }
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        ResponseEventExt event = (ResponseEventExt) responseEvent;
        MessageRecord record = Records.record(response, event.isRetransmission());
        receivedResponses.add(record);
        logger.info("b2b:" + port + " received " + record);
        if (event.isRetransmission()) {
            return;
        }
        ClientTransaction ct = event.getClientTransaction();
        if (ct == null) {
            ct = event.getOriginalTransaction();
        }
        try {
            if (ct != null && forks.contains(ct)) {
                processForkResponse(ct, event);
            } else if (ct != null && ct.getApplicationData() instanceof ServerTransaction) {
                relayResponse((ServerTransaction) ct.getApplicationData(), event);
            }
        } catch (Exception ex) {
            logger.error("b2b: unexpected exception processing response " + response.getStatusCode(), ex);
        }
    }

    private void processForkResponse(ClientTransaction ct, ResponseEvent responseEvent) throws Exception {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        Dialog dialog = responseEvent.getDialog();
        if (status == Response.TRYING) {
            return;
        }
        if (legARequest.getMethod().equals(Request.SUBSCRIBE)) {
            if (status < 200) {
                return;
            }
            finishedForks.add(ct);
            if (status < 300) {
                answered = true;
                legBDialog = dialog != null ? dialog : ct.getDialog();
            } else {
                released = true;
            }
            sendFinalUpstream(status, response);
            legADialog = legAStx.getDialog();
            return;
        }
        if (status < 200) {
            if (response.getHeader(RSeqHeader.NAME) != null) {
                ClientTransaction prack = legBProvider.getNewClientTransaction(dialog.createPrack(response));
                dialog.sendRequest(prack);
            }
            if (!finalSent && ((ToHeader) response.getHeader(ToHeader.NAME)).getTag() != null) {
                relayProvisional(ct, dialog, response);
            }
        } else if (status < 300) {
            finishedForks.add(ct);
            if (answered || finalSent) {
                killFork(ct, dialog, response);
                return;
            }
            answered = true;
            legBDialog = dialog;
            dialog.sendAck(dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber()));
            if (primaryFork != null && primaryFork != ct) {
                finalSent = true;
                legADialog = legAStx.sendForkedResponse(createForkedResponse(status, response));
            } else {
                primaryFork = ct;
                sendFinalUpstream(status, response);
                legADialog = legAStx.getDialog();
            }
        } else {
            finishedForks.add(ct);
            if (finishedForks.size() == forks.size() && sendFinalUpstream(status, response)) {
                released = true;
            }
        }
    }

    private void relayProvisional(ClientTransaction ct, Dialog dialog, Response response) throws Exception {
        if (primaryFork == null) {
            primaryFork = ct;
        }
        int status = response.getStatusCode();
        if (primaryFork == ct) {
            // early dialog on leg B, for UPDATE and the like before the 2xx
            legBDialog = dialog;
            legAStx.sendResponse(createLegAResponse(status, response));
            forkDialogs.put(ct, legAStx.getDialog());
        } else {
            // second fork: one more early dialog towards the shootist
            forkDialogs.put(ct, legAStx.sendForkedResponse(createForkedResponse(status, response)));
        }
    }

    /** the leg B To tag only tells corsac which forked dialog this is, it puts its own tag on leg A */
    private Response createForkedResponse(int status, Response response) throws Exception {
        Response forked = createLegAResponse(status, response);
        ((ToHeader) forked.getHeader(ToHeader.NAME)).setTag(((ToHeader) response.getHeader(ToHeader.NAME)).getTag());
        return forked;
    }

    /** late 2xx from a fork that lost: ACK and BYE it */
    private void killFork(ClientTransaction ct, Dialog dialog, Response response) throws Exception {
        dialog.sendAck(dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber()));
        sendBye(legBProvider, dialog);
        Dialog forked = forkDialogs.get(ct);
        if (forked != null && forked != legADialog) {
            forked.delete();
        }
        forkedDialogsTerminated++;
    }

    private void relayResponse(ServerTransaction st, ResponseEvent responseEvent) throws Exception {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        if (status == Response.TRYING) {
            return;
        }
        Request request = st.getRequest();
        String method = request.getMethod();
        if (method.equals(Request.INVITE) && status / 100 == 2) {
            Dialog dialog = responseEvent.getDialog();
            dialog.sendAck(dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber()));
        }
        Response upstream = messageFactory.createResponse(status, request);
        if (status / 100 == 2 && isTargetRefresh(method) && !method.equals(Request.NOTIFY)) {
            upstream.addHeader(createContact(((TransactionExt) st).getSipProvider() == legAProvider ? port : peerPort));
        }
        copyHeader(response, upstream, ExpiresHeader.NAME);
        st.sendResponse(upstream);
        if (method.equals(Request.NOTIFY) && status / 100 == 2) {
            SubscriptionStateHeader state = (SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME);
            if (state != null && SubscriptionStateHeader.TERMINATED.equalsIgnoreCase(state.getState())) {
                released = true;
                legADialog.delete();
                legBDialog.delete();
            }
        }
    }

    private synchronized boolean sendFinalUpstream(int status, Response downstream) throws Exception {
        if (finalSent || legAStx == null) {
            return false;
        }
        finalSent = true;
        logger.info("b2b:" + port + " sending " + status + " to leg A");
        legAStx.sendResponse(createLegAResponse(status, downstream));
        return true;
    }

    private Response createLegAResponse(int status, Response downstream) throws Exception {
        Response response = messageFactory.createResponse(status, legARequest);
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        if (to.getTag() == null) {
            to.setTag(legATag);
        }
        if (status < 300) {
            response.addHeader(createContact(port));
        }
        if (downstream != null) {
            copyHeader(downstream, response, ExpiresHeader.NAME);
        }
        return response;
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        logger.info("b2b:" + port + " dialog timeout " + timeoutEvent.getReason());
        dialogTimeoutReasons.add(timeoutEvent.getReason());
        try {
            if (timeoutEvent.getReason() == DialogTimeoutEvent.Reason.AckNotReceived && !released) {
                // no ACK on leg A: release both legs
                released = true;
                sendBye(legAProvider, legADialog);
                sendBye(legBProvider, legBDialog);
            } else if (timeoutEvent.getReason() == DialogTimeoutEvent.Reason.EarlyStateTimeout) {
                // leg B never answered: 408 on leg A, CANCEL on leg B
                sendFinalUpstream(Response.REQUEST_TIMEOUT, null);
                released = true;
                cancelPendingForks();
            }
        } catch (Exception ex) {
            logger.error("b2b: could not handle dialog timeout " + timeoutEvent.getReason(), ex);
        }
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
        if (!(messageSentEvent instanceof Response) || transaction != legAStx
                || ((Response) messageSentEvent).getStatusCode() < 200) {
            return;
        }
        ServerTransaction st;
        Request notify;
        synchronized (this) {
            legAFinalOut = true;
            st = pendingNotifyStx;
            notify = pendingNotify;
            pendingNotifyStx = null;
            pendingNotify = null;
        }
        if (st != null) {
            try {
                processInDialogRequest(false, st, notify);
            } catch (Exception ex) {
                logger.error("b2b: could not relay held NOTIFY", ex);
            }
        }
    }

    public void processTimeout(TimeoutEvent e) {
        timeoutEvents++;
        logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.error("b2b: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    private static boolean isTargetRefresh(String method) {
        return method.equals(Request.INVITE) || method.equals(Request.UPDATE) || method.equals(Request.SUBSCRIBE)
                || method.equals(Request.NOTIFY);
    }

    private static void copyHeader(Message from, Message to, String name) {
        Header header = from.getHeader(name);
        if (header != null) {
            to.setHeader((Header) header.clone());
        }
    }

    private ContactHeader createContact(int contactPort) throws Exception {
        SipURI contactUri = addressFactory.createSipURI("b2b", myAddress);
        contactUri.setPort(contactPort);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    /** answered and not released yet */
    public boolean isLinked() {
        return answered && !released;
    }

    public int getForkedDialogsTerminated() {
        return forkedDialogsTerminated;
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

    public int getPort() {
        return port;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public TestAssertion getLinkedAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return isLinked();
            }
        };
    }

    public TestAssertion getForkedDialogsTerminatedAssertion(final int count) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return forkedDialogsTerminated == count;
            }
        };
    }

    public void stop() {
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
