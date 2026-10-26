package test.b2b.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import javax.sip.header.ExpiresHeader;
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
 * Subscribe/Notify B2B. Each leg is its own subscription, everything is
 * relayed in arrival order.
 */
public class SubscribeNotifyBackToBackUserAgent implements SipListener {

    private static final Logger logger = LogManager.getLogger(SubscribeNotifyBackToBackUserAgent.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final SipProvider[] providers = new SipProvider[2];
    private final ListeningPoint[] listeningPoints = new ListeningPoint[2];
    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;

    private final int port2;
    private final int targetPort;

    // upstream (subscriber) leg state
    private FromHeader subscriberFrom;
    private ToHeader subscriberTo;
    private CallIdHeader upstreamCallId;
    private URI subscriberContactUri;
    private String localToTag;
    private long upstreamNotifyCseq;
    private ServerTransaction pendingSubscribeTransaction;

    // downstream (notifier) leg state
    private CallIdHeader downstreamCallId;
    private String downstreamFromTag;
    private Address b2bAddress;
    private Address notifierAddress;
    private String notifierToTag;
    private URI notifierContactUri;
    private long downstreamSubscribeCseq;

    private volatile int notifiesRelayed;
    private volatile int subscribeResponsesRelayed;
    private final List<String> relayedSubscriptionStates = new ArrayList<String>();

    public SubscribeNotifyBackToBackUserAgent(int port1, int port2, int targetPort) {
        this.port2 = port2;
        this.targetPort = targetPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("subnotb2b-" + port1, "gov.nist", transport, false,
                true, false);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            listeningPoints[0] = sipStack.createListeningPoint(myAddress, port1, transport);
            listeningPoints[1] = sipStack.createListeningPoint(myAddress, port2, transport);
            providers[0] = sipStack.createSipProvider(listeningPoints[0]);
            providers[1] = sipStack.createSipProvider(listeningPoints[1]);
            providers[0].addSipListener(this);
            providers[1].addSipListener(this);
            b2bAddress = addressFactory.createAddress(addressFactory.createSipURI("b2b", myAddress));
            notifierAddress = addressFactory.createAddress(addressFactory.createSipURI("notifier", myAddress));
        } catch (Exception ex) {
            throw new RuntimeException("could not create subscribe/notify b2bua", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        logger.info("subnot b2b: received " + method);
        try {
            if (method.equals(Request.SUBSCRIBE)) {
                processSubscribe(requestEvent);
            } else if (method.equals(Request.NOTIFY)) {
                processNotify(requestEvent);
            }
        } catch (Exception ex) {
            logger.error("subnot b2b: unexpected exception processing " + method, ex);
        }
    }

    private void processSubscribe(RequestEvent requestEvent) throws Exception {
        Request request = requestEvent.getRequest();
        ServerTransaction st = requestEvent.getServerTransaction();
        if (st == null) {
            st = providers[0].getNewServerTransaction(request);
        }
        pendingSubscribeTransaction = st;
        Request downstreamSubscribe;
        if (((MessageExt) request).getToHeader().getTag() == null) {
            // initial SUBSCRIBE: open leg B
            subscriberFrom = (FromHeader) ((MessageExt) request).getFromHeader().clone();
            subscriberTo = (ToHeader) ((MessageExt) request).getToHeader().clone();
            upstreamCallId = ((MessageExt) request).getCallIdHeader();
            subscriberContactUri = ((ContactHeader) request.getHeader(ContactHeader.NAME)).getAddress().getURI();
            localToTag = Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));

            downstreamCallId = providers[1].getNewCallId();
            downstreamFromTag = Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));
            downstreamSubscribeCseq = 1;

            SipURI requestUri = addressFactory.createSipURI("notifier", myAddress);
            requestUri.setPort(targetPort);
            downstreamSubscribe = createDownstreamSubscribe(request, requestUri, null, downstreamSubscribeCseq);
        } else {
            // refresh or unsubscribe
            downstreamSubscribeCseq++;
            URI requestUri = notifierContactUri != null ? (URI) notifierContactUri.clone()
                    : addressFactory.createSipURI("notifier", myAddress + ":" + targetPort);
            downstreamSubscribe = createDownstreamSubscribe(request, requestUri, notifierToTag,
                    downstreamSubscribeCseq);
        }
        ClientTransaction ct = providers[1].getNewClientTransaction(downstreamSubscribe);
        logger.info("subnot b2b: forwarding SUBSCRIBE downstream, expires="
                + downstreamSubscribe.getExpires());
        ct.sendRequest();
    }

    private Request createDownstreamSubscribe(Request upstreamRequest, URI requestUri, String toTag, long cseq)
            throws Exception {
        FromHeader fromHeader = headerFactory.createFromHeader(b2bAddress, downstreamFromTag);
        ToHeader toHeader = headerFactory.createToHeader(notifierAddress, toTag);
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.SUBSCRIBE);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port2, transport, null));

        Request subscribe = messageFactory.createRequest(requestUri, Request.SUBSCRIBE, downstreamCallId,
                cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
        EventHeader event = (EventHeader) upstreamRequest.getHeader(EventHeader.NAME);
        if (event != null) {
            subscribe.setHeader((EventHeader) event.clone());
        }
        ExpiresHeader expires = (ExpiresHeader) upstreamRequest.getHeader(ExpiresHeader.NAME);
        if (expires != null) {
            subscribe.setHeader((ExpiresHeader) expires.clone());
        }
        subscribe.setHeader(createContact(port2));
        return subscribe;
    }

    private void processNotify(RequestEvent requestEvent) throws Exception {
        Request notify = requestEvent.getRequest();
        ServerTransaction st = requestEvent.getServerTransaction();
        if (st == null) {
            st = providers[1].getNewServerTransaction(notify);
        }
        st.sendResponse(messageFactory.createResponse(Response.OK, notify));

        notifierToTag = ((MessageExt) notify).getFromHeader().getTag();
        ContactHeader notifierContact = (ContactHeader) notify.getHeader(ContactHeader.NAME);
        if (notifierContact != null) {
            notifierContactUri = notifierContact.getAddress().getURI();
        }

        // re-originate the NOTIFY on leg A
        FromHeader fromHeader = headerFactory.createFromHeader(subscriberTo.getAddress(), localToTag);
        ToHeader toHeader = headerFactory.createToHeader(subscriberFrom.getAddress(), subscriberFrom.getTag());
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(++upstreamNotifyCseq, Request.NOTIFY);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, listeningPoints[0].getPort(), transport, null));

        Request upstreamNotify = messageFactory.createRequest((URI) subscriberContactUri.clone(), Request.NOTIFY,
                upstreamCallId, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
        EventHeader event = (EventHeader) notify.getHeader(EventHeader.NAME);
        if (event != null) {
            upstreamNotify.setHeader((EventHeader) event.clone());
        }
        SubscriptionStateHeader subscriptionState = (SubscriptionStateHeader) notify
                .getHeader(SubscriptionStateHeader.NAME);
        if (subscriptionState != null) {
            upstreamNotify.setHeader((SubscriptionStateHeader) subscriptionState.clone());
            relayedSubscriptionStates.add(subscriptionState.getState());
        }
        upstreamNotify.setHeader(createContact(listeningPoints[0].getPort()));

        ClientTransaction ct = providers[0].getNewClientTransaction(upstreamNotify);
        notifiesRelayed++;
        logger.info("subnot b2b: relaying NOTIFY upstream, state="
                + (subscriptionState == null ? null : subscriptionState.getState()));
        ct.sendRequest();
    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            int status = response.getStatusCode();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            logger.info("subnot b2b: received response " + status + " for " + cseq.getMethod());
            if (!cseq.getMethod().equals(Request.SUBSCRIBE)) {
                return;
            }
            if (status / 100 == 2) {
                notifierToTag = ((ResponseExt) response).getToHeader().getTag();
                ContactHeader notifierContact = (ContactHeader) response.getHeader(ContactHeader.NAME);
                if (notifierContact != null) {
                    notifierContactUri = notifierContact.getAddress().getURI();
                }
            }
            ServerTransaction st = pendingSubscribeTransaction;
            if (st == null) {
                return;
            }
            Response newResponse = messageFactory.createResponse(status, st.getRequest());
            ToHeader newTo = (ToHeader) newResponse.getHeader(ToHeader.NAME);
            if (newTo.getTag() == null) {
                newTo.setTag(localToTag);
            }
            ExpiresHeader expires = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
            if (expires != null) {
                newResponse.setHeader((ExpiresHeader) expires.clone());
            }
            newResponse.setHeader(createContact(listeningPoints[0].getPort()));
            subscribeResponsesRelayed++;
            logger.info("subnot b2b: relaying SUBSCRIBE response " + status + " upstream");
            st.sendResponse(newResponse);
        } catch (Exception ex) {
            logger.error("subnot b2b: unexpected exception processing response", ex);
        }
    }

    private ContactHeader createContact(int port) throws Exception {
        SipURI contactUri = addressFactory.createSipURI("b2b", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("subnot b2b: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public int getNotifiesRelayed() {
        return notifiesRelayed;
    }

    public int getSubscribeResponsesRelayed() {
        return subscribeResponsesRelayed;
    }

    public List<String> getRelayedSubscriptionStates() {
        return relayedSubscriptionStates;
    }

    public void stop() {
        sipStack.stop();
    }
}
