package test.unit.gov.nist.javax.sip.stack.rfc4028;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.ProtocolObjects;
import test.unit.gov.nist.javax.sip.stack.rfc4028.Records.MessageRecord;

/**
 * A very simple record-routing stateful proxy, RFC_4028_AUTO_SUPPORTED on, dialog
 * support off: every response hits the dialog == null (proxy) branch of the stack.
 */
public class TestProxy implements SipListener {

    private static final Logger logger = LogManager.getLogger(TestProxy.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final int targetPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    /** as handed to the listener, i.e. after the stack touched them */
    private final List<MessageRecord> upstreamResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private final List<MessageRecord> forwardedRequests = Collections
            .synchronizedList(new ArrayList<MessageRecord>());
    private volatile boolean ioExceptionSeen;

    public TestProxy(int port, int targetPort) {
        this.port = port;
        this.targetPort = targetPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("testproxy-" + port, "gov.nist", transport, false,
                false, false, SessionTimerMode.STACK.stackProperties());
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create proxy", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        try {
            Request forwarded = (Request) request.clone();
            stripOwnRoute(forwarded);

            if (request.getMethod().equals(Request.ACK)) {
                // end to end, stateless
                sipProvider.sendRequest(forwarded);
                forwardedRequests.add(Records.record(forwarded));
                return;
            }

            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }

            boolean initialInvite = request.getMethod().equals(Request.INVITE)
                    && ((ToHeader) request.getHeader(ToHeader.NAME)).getTag() == null;
            if (initialInvite) {
                SipURI targetUri = addressFactory.createSipURI("callee", myAddress);
                targetUri.setPort(targetPort);
                targetUri.setLrParam();
                Address targetAddress = addressFactory.createAddress(targetUri);
                RouteHeader route = headerFactory.createRouteHeader(targetAddress);
                forwarded.addFirst(route);

                SipURI recordRouteUri = addressFactory.createSipURI("proxy", myAddress);
                recordRouteUri.setPort(port);
                recordRouteUri.setLrParam();
                RecordRouteHeader recordRoute = headerFactory
                        .createRecordRouteHeader(addressFactory.createAddress(recordRouteUri));
                forwarded.addHeader(recordRoute);
            }

            ViaHeader via = headerFactory.createViaHeader(myAddress, port, transport, null);
            forwarded.addFirst(via);

            ClientTransaction ct = sipProvider.getNewClientTransaction(forwarded);
            ct.setApplicationData(st);
            forwardedRequests.add(Records.record(forwarded));
            logger.info("proxy:" + port + " forwarding " + request.getMethod());
            ct.sendRequest();
        } catch (Exception ex) {
            logger.error("proxy: could not forward request", ex);
        }
    }

    private void stripOwnRoute(Request request) {
        RouteHeader top = (RouteHeader) request.getHeader(RouteHeader.NAME);
        if (top != null && top.getAddress().getURI().isSipURI()) {
            SipURI uri = (SipURI) top.getAddress().getURI();
            if (uri.getPort() == port && myAddress.equals(uri.getHost())) {
                request.removeFirst(RouteHeader.NAME);
            }
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        // stateful proxy MUST NOT forward 100 Trying
        if (response.getStatusCode() == Response.TRYING) {
            return;
        }
        MessageRecord record = Records.record(response);
        upstreamResponses.add(record);
        logger.info("proxy:" + port + " upstream " + record);
        try {
            Response forwarded = (Response) response.clone();
            forwarded.removeFirst(ViaHeader.NAME);
            ClientTransaction ct = responseEvent.getClientTransaction();
            if (ct != null && ct.getApplicationData() instanceof ServerTransaction) {
                ((ServerTransaction) ct.getApplicationData()).sendResponse(forwarded);
            } else {
                // ctx already gone, the UAS is retransmitting: forward statelessly
                sipProvider.sendResponse(forwarded);
            }
        } catch (Exception ex) {
            logger.error("proxy: could not forward response", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.error("proxy: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public List<MessageRecord> getForwardedInvites() {
        return Records.requests(forwardedRequests, Request.INVITE);
    }

    public List<MessageRecord> getUpstreamInviteOks() {
        return Records.distinctResponses(upstreamResponses, Response.OK, Request.INVITE);
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        sipStack.stop();
    }
}
