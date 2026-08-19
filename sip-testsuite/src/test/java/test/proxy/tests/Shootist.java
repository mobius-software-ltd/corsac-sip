package test.proxy.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 */
public class Shootist implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final int proxyPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;
    private ListeningPoint listeningPoint;

    private final Timer timer = new Timer();

    public int sessionExpires;
    public long byeDelay = 500;
    public long refreshDelay = 1000;

    private Dialog dialog;
    private volatile boolean initialAcked;

    private volatile boolean okSeen;
    private volatile boolean byeOkSeen;
    private volatile boolean rejected422Seen;
    private volatile int minSEOffered;
    private volatile boolean timeout408Seen;
    private volatile boolean requestTerminatedSeen;
    private volatile boolean refreshOkSeen;
    private volatile int keepAliveOkCount;
    private final List<Integer> unexpectedFinalResponses = Collections.synchronizedList(new ArrayList<Integer>());

    public Shootist(int port, int proxyPort) {
        this.port = port;
        this.proxyPort = proxyPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, true, false,
                false);
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
            sendInvite(sessionExpires);
        } catch (Exception ex) {
            throw new RuntimeException("could not send INVITE", ex);
        }
    }

    private void sendInvite(int sessionInterval) throws Exception {
        SipURI fromUri = addressFactory.createSipURI("caller", "test.mobius.local");
        Address fromAddress = addressFactory.createAddress(fromUri);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress,
                Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));

        SipURI toUri = addressFactory.createSipURI("callee", "test.mobius.local");
        Address toAddress = addressFactory.createAddress(toUri);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        SipURI requestUri = addressFactory.createSipURI("callee", myAddress);
        requestUri.setPort(proxyPort);

        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        Request request = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader,
                fromHeader, toHeader, viaHeaders, maxForwards);

        request.addHeader(createContact());

        SipURI routeUri = addressFactory.createSipURI(null, myAddress);
        routeUri.setPort(proxyPort);
        routeUri.setLrParam();
        RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeUri));
        request.setHeader(routeHeader);

        if (sessionInterval > 0) {
            request.addHeader(headerFactory.createSupportedHeader("timer"));
            SessionExpiresHeader se = ((HeaderFactoryImpl) headerFactory)
                    .createSessionExpiresHeader(sessionInterval);
            se.setRefresher("uac");
            request.addHeader(se);
        }

        ClientTransaction inviteTransaction = sipProvider.getNewClientTransaction(request);
        logger.info("shootist: sending INVITE, Session-Expires=" + sessionInterval);
        inviteTransaction.sendRequest();
    }

    /** Start pinging the proxy with OPTIONS keepalives at the given interval. */
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
            SipURI requestUri = addressFactory.createSipURI("proxy", myAddress);
            requestUri.setPort(proxyPort);

            SipURI fromUri = addressFactory.createSipURI("caller", "test.mobius.local");
            FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri),
                    Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));
            SipURI toUri = addressFactory.createSipURI("proxy", "test.mobius.local");
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
        SipURI contactUri = addressFactory.createSipURI("caller", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(transport);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public synchronized void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
        if (responseEvent.getClientTransaction() == null && responseEventExt.isRetransmission()) {
            logger.info("shootist: dropping retransmission of " + status);
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
            if (!cseq.getMethod().equals(Request.INVITE)) {
                return;
            }
            if (status < 200) {
                return;
            }
            if (status == Response.OK) {
                Dialog responseDialog = responseEvent.getDialog();
                Request ack = responseDialog.createAck(cseq.getSeqNumber());
                responseDialog.sendAck(ack);
                if (!initialAcked) {
                    initialAcked = true;
                    okSeen = true;
                    this.dialog = responseDialog;
                    if (sessionExpires > 0) {
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                sendSessionRefresh();
                            }
                        }, refreshDelay);
                    } else {
                        scheduleBye();
                    }
                } else {
                    // refresh OK
                    refreshOkSeen = true;
                    scheduleBye();
                }
            } else if (status == 422) {
                rejected422Seen = true;
                MinSE minSe = (MinSE) response.getHeader(MinSE.NAME);
                minSEOffered = minSe.getExpires();
                logger.info("shootist: 422 received, retrying with Session-Expires=" + minSEOffered);
                sessionExpires = minSEOffered;
                sendInvite(minSEOffered);
            } else if (status == Response.REQUEST_TIMEOUT) {
                timeout408Seen = true;
            } else if (status == Response.REQUEST_TERMINATED) {
                requestTerminatedSeen = true;
            } else {
                unexpectedFinalResponses.add(status);
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception processing response " + status, ex);
        }
    }

    private void sendSessionRefresh() {
        try {
            Request refresh = dialog.createRequest(Request.INVITE);
            refresh.setHeader(createContact());
            refresh.addHeader(headerFactory.createSupportedHeader("timer"));
            SessionExpiresHeader se = ((HeaderFactoryImpl) headerFactory).createSessionExpiresHeader(sessionExpires);
            se.setRefresher("uac");
            refresh.addHeader(se);
            ClientTransaction refreshTransaction = sipProvider.getNewClientTransaction(refresh);
            logger.info("shootist: sending session refresh re-INVITE");
            dialog.sendRequest(refreshTransaction);
        } catch (Exception ex) {
            logger.error("shootist: could not send session refresh", ex);
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
            }
        } catch (Exception ex) {
            logger.error("shootist: unexpected exception", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
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

    public boolean isOkSeen() {
        return okSeen;
    }

    public boolean isByeOkSeen() {
        return byeOkSeen;
    }

    public boolean isRejected422Seen() {
        return rejected422Seen;
    }

    public int getMinSEOffered() {
        return minSEOffered;
    }

    public boolean isTimeout408Seen() {
        return timeout408Seen;
    }

    public boolean isRequestTerminatedSeen() {
        return requestTerminatedSeen;
    }

    public boolean isRefreshOkSeen() {
        return refreshOkSeen;
    }

    public int getKeepAliveOkCount() {
        return keepAliveOkCount;
    }

    public List<Integer> getUnexpectedFinalResponses() {
        return unexpectedFinalResponses;
    }

    public TestAssertion getCompletedCallAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return okSeen && byeOkSeen;
            }
        };
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
