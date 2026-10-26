package test.b2b.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * Notifying UA. In early mode the NOTIFY goes out before the 200.
 */
public class Notifier implements SipListener {

    private static final Logger logger = LogManager.getLogger(Notifier.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final boolean earlyNotify;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    // subscription state learned from the initial SUBSCRIBE
    private CallIdHeader callIdHeader;
    private Address subscriberAddress;
    private String subscriberTag;
    private Address ownAddress;
    private String ownToTag;
    private URI subscriberContactUri;
    private EventHeader eventHeader;
    private long notifyCseq;

    private volatile boolean subscribeSeen;
    private volatile boolean unsubscribeSeen;
    private volatile int notifyOkCount;
    private final List<String> sentStates = new ArrayList<String>();

    public Notifier(int port, boolean earlyNotify) {
        this.port = port;
        this.earlyNotify = earlyNotify;
        ProtocolObjects protocolObjects = new ProtocolObjects("notifier-" + port, "gov.nist", transport,
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
            throw new RuntimeException("could not create event notifier", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        if (!request.getMethod().equals(Request.SUBSCRIBE)) {
            return;
        }
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            final ServerTransaction serverTransaction = st;
            ExpiresHeader expires = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
            int expiresValue = expires == null ? 3600 : expires.getExpires();

            if (((MessageExt) request).getToHeader().getTag() == null) {
                // initial subscription
                subscribeSeen = true;
                callIdHeader = ((MessageExt) request).getCallIdHeader();
                subscriberAddress = ((MessageExt) request).getFromHeader().getAddress();
                subscriberTag = ((MessageExt) request).getFromHeader().getTag();
                ownAddress = ((MessageExt) request).getToHeader().getAddress();
                ownToTag = Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));
                subscriberContactUri = ((ContactHeader) request.getHeader(ContactHeader.NAME)).getAddress()
                        .getURI();
                eventHeader = (EventHeader) request.getHeader(EventHeader.NAME);

                final Response subscribeOk = createSubscribeResponse(Response.OK, request, expiresValue);
                if (earlyNotify) {
                    logger.info("notifier: early mode - sending NOTIFY before the 200");
                    sendNotify(SubscriptionStateHeader.ACTIVE);
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                serverTransaction.sendResponse(subscribeOk);
                            } catch (Exception ex) {
                                logger.error("notifier: could not send delayed 200", ex);
                            }
                        }
                    }, 500);
                } else {
                    serverTransaction.sendResponse(subscribeOk);
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            sendNotify(SubscriptionStateHeader.ACTIVE);
                        }
                    }, 200);
                }
            } else {
                // refresh or unsubscribe
                serverTransaction.sendResponse(createSubscribeResponse(Response.OK, request, expiresValue));
                if (expiresValue == 0) {
                    unsubscribeSeen = true;
                    logger.info("notifier: un-SUBSCRIBE received, terminating subscription");
                    sendNotify(SubscriptionStateHeader.TERMINATED);
                }
            }
        } catch (Exception ex) {
            logger.error("notifier: unexpected exception processing SUBSCRIBE", ex);
        }
    }

    private Response createSubscribeResponse(int status, Request request, int expiresValue) throws Exception {
        Response response = messageFactory.createResponse(status, request);
        ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
        if (toHeader.getTag() == null) {
            toHeader.setTag(ownToTag);
        }
        response.addHeader(createContact());
        response.addHeader(headerFactory.createExpiresHeader(expiresValue));
        return response;
    }

    private synchronized void sendNotify(String state) {
        try {
            FromHeader fromHeader = headerFactory.createFromHeader(ownAddress, ownToTag);
            ToHeader toHeader = headerFactory.createToHeader(subscriberAddress, subscriberTag);
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(++notifyCseq, Request.NOTIFY);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

            Request notify = messageFactory.createRequest((URI) subscriberContactUri.clone(), Request.NOTIFY,
                    callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
            if (eventHeader != null) {
                notify.setHeader((EventHeader) eventHeader.clone());
            }
            SubscriptionStateHeader subscriptionState = headerFactory.createSubscriptionStateHeader(state);
            if (SubscriptionStateHeader.TERMINATED.equalsIgnoreCase(state)) {
                subscriptionState.setReasonCode("timeout");
            }
            notify.addHeader(subscriptionState);
            notify.setHeader(createContact());

            ClientTransaction ct = sipProvider.getNewClientTransaction(notify);
            sentStates.add(state);
            logger.info("notifier: sending NOTIFY, state=" + state);
            ct.sendRequest();
        } catch (Exception ex) {
            logger.error("notifier: could not send NOTIFY", ex);
        }
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("notifier", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        logger.info("notifier: received " + response.getStatusCode() + " for " + cseq.getMethod());
        if (cseq.getMethod().equals(Request.NOTIFY) && response.getStatusCode() == Response.OK) {
            notifyOkCount++;
        }
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("notifier: transaction timeout:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("notifier: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isSubscribeSeen() {
        return subscribeSeen;
    }

    public boolean isUnsubscribeSeen() {
        return unsubscribeSeen;
    }

    public int getNotifyOkCount() {
        return notifyOkCount;
    }

    public List<String> getSentStates() {
        return sentStates;
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
