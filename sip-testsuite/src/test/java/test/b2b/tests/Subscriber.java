package test.b2b.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
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
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
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

import gov.nist.javax.sip.message.MessageExt;
import gov.nist.javax.sip.message.ResponseExt;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * Subscribing UA. 
 */
public class Subscriber implements SipListener {

    private static final Logger logger = LogManager.getLogger(Subscriber.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";
    private static final String EVENT_TYPE = "presence";

    private final int port;
    private final int peerPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    private CallIdHeader callIdHeader;
    private String fromTag;
    private String remoteToTag;
    private URI remoteContactUri;

    private volatile boolean subscribeOkSeen;
    private volatile boolean unsubscribeOkSeen;
    private volatile int activeNotifyCount;
    private volatile boolean terminatedNotifySeen;
    private volatile boolean firstNotifyBeforeSubscribeOk;
    private volatile boolean sawAnyNotify;
    private volatile boolean unsubscribeSent;
    private final List<String> notifiedStates = new ArrayList<String>();

    public Subscriber(int port, int peerPort) {
        this.port = port;
        this.peerPort = peerPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("subscriber-" + port, "gov.nist", transport,
                false, false, false);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create event subscriber", ex);
        }
    }

    public void sendSubscribe() {
        try {
            callIdHeader = sipProvider.getNewCallId();
            fromTag = "evtsub-" + port;
            SipURI requestUri = addressFactory.createSipURI("notifier", myAddress);
            requestUri.setPort(peerPort);
            Request subscribe = buildSubscribe(requestUri, null, 1, 300);
            ClientTransaction ct = sipProvider.getNewClientTransaction(subscribe);
            logger.info("subscriber: sending SUBSCRIBE");
            ct.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send SUBSCRIBE", ex);
        }
    }

    private Request buildSubscribe(URI requestUri, String toTag, long cseq, int expires) throws Exception {
        SipURI fromUri = addressFactory.createSipURI("subscriber", "test.mobius.local");
        Address fromAddress = addressFactory.createAddress(fromUri);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, fromTag);

        SipURI toUri = addressFactory.createSipURI("notifier", "test.mobius.local");
        ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), toTag);

        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.SUBSCRIBE);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

        Request subscribe = messageFactory.createRequest(requestUri, Request.SUBSCRIBE, callIdHeader, cSeqHeader,
                fromHeader, toHeader, viaHeaders, maxForwards);
        subscribe.addHeader(headerFactory.createEventHeader(EVENT_TYPE));
        subscribe.addHeader(headerFactory.createExpiresHeader(expires));

        SipURI contactUri = addressFactory.createSipURI("subscriber", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        subscribe.addHeader(headerFactory.createContactHeader(addressFactory.createAddress(contactUri)));
        return subscribe;
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        if (!request.getMethod().equals(Request.NOTIFY)) {
            return;
        }
        try {
            if (!sawAnyNotify) {
                sawAnyNotify = true;
                if (!subscribeOkSeen) {
                    firstNotifyBeforeSubscribeOk = true;
                    logger.info("subscriber: NOTIFY arrived before the SUBSCRIBE 2xx (early NOTIFY)");
                }
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            st.sendResponse(messageFactory.createResponse(Response.OK, request));

            remoteToTag = ((MessageExt) request).getFromHeader().getTag();
            ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
            if (contact != null && remoteContactUri == null) {
                remoteContactUri = contact.getAddress().getURI();
            }

            SubscriptionStateHeader subscriptionState = (SubscriptionStateHeader) request
                    .getHeader(SubscriptionStateHeader.NAME);
            String state = subscriptionState == null ? "" : subscriptionState.getState();
            notifiedStates.add(state);
            logger.info("subscriber: NOTIFY received, state=" + state);
            if (SubscriptionStateHeader.TERMINATED.equalsIgnoreCase(state)) {
                terminatedNotifySeen = true;
            } else {
                activeNotifyCount++;
            }
            maybeUnsubscribe();
        } catch (Exception ex) {
            logger.error("subscriber: unexpected exception processing NOTIFY", ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        logger.info("subscriber: received " + status + " for " + cseq.getMethod());
        try {
            if (!cseq.getMethod().equals(Request.SUBSCRIBE) || status / 100 != 2) {
                return;
            }
            if (cseq.getSeqNumber() == 1) {
                subscribeOkSeen = true;
                remoteToTag = ((ResponseExt) response).getToHeader().getTag();
                ContactHeader contact = (ContactHeader) response.getHeader(ContactHeader.NAME);
                if (contact != null) {
                    remoteContactUri = contact.getAddress().getURI();
                }
                maybeUnsubscribe();
            } else {
                unsubscribeOkSeen = true;
            }
        } catch (Exception ex) {
            logger.error("subscriber: unexpected exception processing response", ex);
        }
    }

    private void maybeUnsubscribe() {
        if (!subscribeOkSeen || activeNotifyCount == 0 || unsubscribeSent) {
            return;
        }
        unsubscribeSent = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    URI requestUri;
                    if (remoteContactUri != null) {
                        requestUri = (URI) remoteContactUri.clone();
                    } else {
                        SipURI uri = addressFactory.createSipURI("notifier", myAddress);
                        uri.setPort(peerPort);
                        requestUri = uri;
                    }
                    Request unsubscribe = buildSubscribe(requestUri, remoteToTag, 2, 0);
                    ClientTransaction ct = sipProvider.getNewClientTransaction(unsubscribe);
                    logger.info("subscriber: sending un-SUBSCRIBE");
                    ct.sendRequest();
                } catch (Exception ex) {
                    logger.error("subscriber: could not unsubscribe", ex);
                }
            }
        }, 200);
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("subscriber: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isSubscribeOkSeen() {
        return subscribeOkSeen;
    }

    public boolean isUnsubscribeOkSeen() {
        return unsubscribeOkSeen;
    }

    public int getActiveNotifyCount() {
        return activeNotifyCount;
    }

    public boolean isTerminatedNotifySeen() {
        return terminatedNotifySeen;
    }

    public boolean isFirstNotifyBeforeSubscribeOk() {
        return firstNotifyBeforeSubscribeOk;
    }

    public List<String> getNotifiedStates() {
        return notifiedStates;
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
