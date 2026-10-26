package test.proxy.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

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
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.SessionExpires;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * Stateful test proxy: parallel/sequential/recursive forking,
 * record-routing, Min-SE, keepalives.
 */
public class TestProxy implements SipListener {

    public enum Mode {
        PARALLEL, SEQUENTIAL, RECURSE
    }

    private static final Logger logger = LogManager.getLogger(TestProxy.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final int[] targetPorts;
    private final Mode mode;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    public long branchTimeout;
    public int minSE;
    private final ConcurrentHashMap<Integer, Long> perBranchTimeouts = new ConcurrentHashMap<Integer, Long>();
    public boolean cancelRemainingBranchesOn2xx;
    public Registrar locationService;
    public String locationServiceUser;

    private ServerTransaction inviteServerTransaction;
    private Request originalInvite;
    private final List<ClientTransaction> branches = Collections.synchronizedList(new ArrayList<ClientTransaction>());
    private final List<ClientTransaction> abandonedBranches = Collections
            .synchronizedList(new ArrayList<ClientTransaction>());
    private int nextSequentialTarget;
    private volatile boolean answered;
    private volatile boolean timedOut;
    private volatile boolean finalRelayed;

    private final Timer timer = new Timer();

    // observability for the tests
    private volatile boolean rejected422;
    private volatile int redirectsFollowed;
    private volatile int canceledBranches;
    private volatile int sessionRefreshesForwarded;
    private volatile int rejectedTargets;
    private volatile int keepAlivesAnswered;

    public TestProxy(int port, Mode mode, int[] targetPorts) {
        this.port = port;
        this.mode = mode;
        this.targetPorts = targetPorts;
        ProtocolObjects protocolObjects = new ProtocolObjects("testproxy-" + port, "gov.nist", transport, false,
                false, false);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create test proxy", ex);
        }
    }



    /** parallel only, one target gets its own timeout. */
    public void setPerBranchTimeoutMs(int targetPort, long timeoutMs) {
        perBranchTimeouts.put(Integer.valueOf(targetPort), Long.valueOf(timeoutMs));
    }



    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        String toTag = ((ToHeader) request.getHeader(ToHeader.NAME)).getTag();
        logger.info("proxy: received " + method + " toTag=" + toTag);
        try {
            if (method.equals(Request.INVITE) && toTag == null) {
                processInitialInvite(requestEvent);
            } else if (method.equals(Request.CANCEL)) {
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st != null) {
                    st.sendResponse(messageFactory.createResponse(Response.OK, request));
                }
                cancelBranches();
            } else if (method.equals(Request.ACK)) {
                forwardInDialog(request);
            } else if (method.equals(Request.OPTIONS)) {
                // UDP keepAlive handling
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = sipProvider.getNewServerTransaction(request);
                }
                Response pong = messageFactory.createResponse(Response.OK, request);
                ToHeader toHeader = (ToHeader) pong.getHeader(ToHeader.NAME);
                if (toHeader.getTag() == null) {
                    toHeader.setTag(generateTag());
                }
                st.sendResponse(pong);
                keepAlivesAnswered++;
            } else {
                // subsequent requests just follow the route set
                if (method.equals(Request.INVITE)) {
                    sessionRefreshesForwarded++;
                }
                forwardInDialog(request);
            }
        } catch (Exception ex) {
            logger.error("proxy: unexpected exception processing " + method, ex);
        }
    }

    private void processInitialInvite(RequestEvent requestEvent) throws Exception {
        Request request = requestEvent.getRequest();
        ServerTransaction st = requestEvent.getServerTransaction();
        if (st == null) {
            st = sipProvider.getNewServerTransaction(request);
        }
        this.inviteServerTransaction = st;
        // pop the Route only if it is ours
        Request cleaned = (Request) request.clone();
        RouteHeader topRoute = (RouteHeader) cleaned.getHeader(RouteHeader.NAME);
        if (topRoute != null && isMyUri(topRoute.getAddress())) {
            cleaned.removeFirst(RouteHeader.NAME);
        }
        this.originalInvite = cleaned;

        st.sendResponse(messageFactory.createResponse(Response.TRYING, request));

        if (minSE > 0) {
            SessionExpiresHeader se = (SessionExpiresHeader) request.getHeader(SessionExpires.NAME);
            if (se == null || se.getExpires() < minSE) {
                Response tooSmall = messageFactory.createResponse(422, request);
                ((ToHeader) tooSmall.getHeader(ToHeader.NAME)).setTag(generateTag());
                MinSE minSeHeader = new MinSE();
                minSeHeader.setExpires(minSE);
                tooSmall.setHeader(minSeHeader);
                st.sendResponse(tooSmall);
                rejected422 = true;
                logger.info("proxy: rejected INVITE with 422, Min-SE=" + minSE);
                return;
            }
        }

        if (mode == Mode.PARALLEL) {
            for (int targetPort : resolveTargets()) {
                sendBranch(st, originalInvite, targetPort);
            }
            if (branchTimeout > 0) {
                timer.schedule(new BranchTimeoutTask(), branchTimeout);
            }
        } else {
            nextSequentialTarget = 0;
            sendBranch(st, originalInvite, targetPorts[nextSequentialTarget++]);
        }
    }

    private ClientTransaction sendBranch(ServerTransaction st, Request request, int targetPort) throws Exception {
        Request newRequest = (Request) request.clone();

        SipURI routeUri = addressFactory.createSipURI(null, myAddress);
        routeUri.setPort(targetPort);
        routeUri.setLrParam();
        RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeUri));
        newRequest.addFirst(routeHeader);

        ViaHeader viaHeader = headerFactory.createViaHeader(myAddress, port, transport, null);
        newRequest.addFirst(viaHeader);

        SipURI recordRouteUri = addressFactory.createSipURI(null, myAddress);
        recordRouteUri.setPort(port);
        recordRouteUri.setLrParam();
        RecordRouteHeader recordRouteHeader = headerFactory
                .createRecordRouteHeader(addressFactory.createAddress(recordRouteUri));
        newRequest.addHeader(recordRouteHeader);

        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(newRequest);
        clientTransaction.setApplicationData(st);
        branches.add(clientTransaction);
        logger.info("proxy: branching " + request.getMethod() + " to port " + targetPort);
        clientTransaction.sendRequest();
        if (mode == Mode.SEQUENTIAL && branchTimeout > 0) {
            timer.schedule(new SequentialSearchTimeoutTask(clientTransaction), branchTimeout);
        }
        if (mode == Mode.PARALLEL) {
            Long perBranchTimeout = perBranchTimeouts.get(Integer.valueOf(targetPort));
            if (perBranchTimeout != null) {
                timer.schedule(new PerBranchTimeoutTask(clientTransaction), perBranchTimeout.longValue());
            }
        }
        return clientTransaction;
    }

    private int[] resolveTargets() {
        if (locationService == null) {
            return targetPorts;
        }
        List<SipURI> bindings = locationService.getBindings(locationServiceUser);
        int[] ports = new int[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            ports[i] = bindings.get(i).getPort();
        }
        logger.info("proxy: location service resolved " + locationServiceUser + " to " + bindings);
        return ports;
    }

    private void forwardInDialog(Request request) throws Exception {
        Request newRequest = (Request) request.clone();
        RouteHeader topRoute = (RouteHeader) newRequest.getHeader(RouteHeader.NAME);
        if (topRoute != null && isMyUri(topRoute.getAddress())) {
            newRequest.removeFirst(RouteHeader.NAME);
        }
        logger.info("proxy: forwarding in-dialog " + request.getMethod());
        sipProvider.sendRequest(newRequest);
    }

    private boolean isMyUri(Address address) {
        if (!(address.getURI() instanceof SipURI)) {
            return false;
        }
        SipURI uri = (SipURI) address.getURI();
        return uri.getPort() == port;
    }

    private void cancelBranches() {
        synchronized (branches) {
            for (ClientTransaction branch : branches) {
                cancelBranch(branch);
            }
        }
    }

    private void cancelBranch(ClientTransaction branch) {
        try {
            if (branch.getState() == TransactionState.PROCEEDING) {
                Request cancel = branch.createCancel();
                sipProvider.getNewClientTransaction(cancel).sendRequest();
                canceledBranches++;
            }
        } catch (Exception ex) {
            logger.error("proxy: could not cancel branch", ex);
        }
    }

    // proxy timeout: CANCEL everything still ringing, the 487s become the finals
    private class BranchTimeoutTask extends TimerTask {
        @Override
        public void run() {
            if (answered) {
                return;
            }
            timedOut = true;
            logger.info("proxy: proxy timeout fired, canceling all branches");
            cancelBranches();
        }
    }

    // per-branch timeout: kill just this branch, the fork carries on
    private class PerBranchTimeoutTask extends TimerTask {
        private final ClientTransaction branch;

        PerBranchTimeoutTask(ClientTransaction branch) {
            this.branch = branch;
        }

        @Override
        public void run() {
            if (answered || branch.getState() == TransactionState.COMPLETED
                    || branch.getState() == TransactionState.TERMINATED) {
                return;
            }
            logger.info("proxy: per-branch timeout fired, canceling one branch");
            abandonedBranches.add(branch);
            cancelBranch(branch);
        }
    }

    // SST fired: abandon + CANCEL the branch, try the next target
    private class SequentialSearchTimeoutTask extends TimerTask {
        private final ClientTransaction branch;

        SequentialSearchTimeoutTask(ClientTransaction branch) {
            this.branch = branch;
        }

        @Override
        public void run() {
            if (answered || branch.getState() == TransactionState.COMPLETED
                    || branch.getState() == TransactionState.TERMINATED) {
                return;
            }
            logger.info("proxy: sequential search timeout fired, hunting to the next target");
            abandonedBranches.add(branch);
            cancelBranch(branch);
            try {
                if (nextSequentialTarget < targetPorts.length) {
                    sendBranch(inviteServerTransaction, originalInvite, targetPorts[nextSequentialTarget++]);
                } else {
                    timedOut = true;
                }
            } catch (Exception ex) {
                logger.error("proxy: could not hunt to next target", ex);
            }
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            int status = response.getStatusCode();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            logger.info("proxy: received response " + status + " for " + cseq.getMethod());
            if (status == Response.TRYING || !cseq.getMethod().equals(Request.INVITE)) {
                return;
            }
            if (responseEvent.getClientTransaction() != null
                    && abandonedBranches.contains(responseEvent.getClientTransaction())) {
                // abandoned branch, swallow
                logger.info("proxy: swallowing " + status + " of abandoned branch");
                return;
            }
            if (timedOut) {
                // timed out: forward the best final once, drop the rest
                if (status >= 300 && !finalRelayed) {
                    finalRelayed = true;
                    relay(responseEvent, response);
                }
                return;
            }
            if (status >= 300 && status < 400 && mode == Mode.RECURSE && redirectsFollowed < 3) {
                ContactHeader contact = (ContactHeader) response.getHeader(ContactHeader.NAME);
                if (contact != null && contact.getAddress().getURI() instanceof SipURI) {
                    SipURI target = (SipURI) contact.getAddress().getURI();
                    redirectsFollowed++;
                    logger.info("proxy: recursing on " + status + " to port " + target.getPort());
                    sendBranch(inviteServerTransaction, originalInvite, target.getPort());
                    return;
                }
            }
            if (status >= 300 && mode == Mode.SEQUENTIAL && nextSequentialTarget < targetPorts.length) {
                rejectedTargets++;
                logger.info("proxy: target failed with " + status + ", trying next target");
                sendBranch(inviteServerTransaction, originalInvite, targetPorts[nextSequentialTarget++]);
                return;
            }
            if (status >= 200 && status < 300) {
                answered = true;
                if (cancelRemainingBranchesOn2xx) {
                    ClientTransaction winner = responseEvent.getClientTransaction();
                    synchronized (branches) {
                        for (ClientTransaction branch : branches) {
                            if (branch != winner && branch.getState() == TransactionState.PROCEEDING) {
                                abandonedBranches.add(branch);
                                cancelBranch(branch);
                            }
                        }
                    }
                }
            }
            relay(responseEvent, response);
        } catch (Exception ex) {
            logger.error("proxy: unexpected exception processing response", ex);
        }
    }

    private void relay(ResponseEvent responseEvent, Response response) throws Exception {
        Response newResponse = (Response) response.clone();
        newResponse.removeFirst(ViaHeader.NAME);
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        ServerTransaction st = clientTransaction == null ? inviteServerTransaction
                : (ServerTransaction) clientTransaction.getApplicationData();
        if (st == null) {
            st = inviteServerTransaction;
        }
        try {
            st.sendResponse(newResponse);
        } catch (Exception ex) {
            // second forked 2xx, the stx is done with, forward statelessly
            logger.info("proxy: server transaction refused response, relaying statelessly: " + ex.getMessage());
            sipProvider.sendResponse(newResponse);
        }
    }

    private String generateTag() {
        return Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("proxy: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isRejected422() {
        return rejected422;
    }

    public int getRedirectsFollowed() {
        return redirectsFollowed;
    }

    public int getCanceledBranches() {
        return canceledBranches;
    }

    public int getSessionRefreshesForwarded() {
        return sessionRefreshesForwarded;
    }

    public int getRejectedTargets() {
        return rejectedTargets;
    }

    public int getKeepAlivesAnswered() {
        return keepAlivesAnswered;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
