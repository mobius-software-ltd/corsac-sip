package test.unit.gov.nist.javax.sip.stack.malformed;

import java.util.Properties;

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

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * A very simple record-routing stateful proxy, dialog support off. Requests and responses
 * are cloned and sent on, so whatever the stack kept of a malformed header goes on with them.
 */
public class Proxy implements SipListener {

    private static final StackLogger logger = CommonLogger.getLogger(Proxy.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int targetPort;
    private final String transport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    /** transport is udp or tcp, the target is reached the same way */
    public Proxy(int port, int targetPort, String messageProcessorFactory, String transport) {
        this.port = port;
        this.targetPort = targetPort;
        this.transport = transport;
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", messageProcessorFactory);
        ProtocolObjects protocolObjects = new ProtocolObjects("proxy-" + port, "gov.nist", transport, false,
                false, false, properties);
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
                targetUri.setTransportParam(transport);
                targetUri.setLrParam();
                Address targetAddress = addressFactory.createAddress(targetUri);
                RouteHeader route = headerFactory.createRouteHeader(targetAddress);
                forwarded.addFirst(route);

                SipURI recordRouteUri = addressFactory.createSipURI("proxy", myAddress);
                recordRouteUri.setPort(port);
                recordRouteUri.setTransportParam(transport);
                recordRouteUri.setLrParam();
                RecordRouteHeader recordRoute = headerFactory
                        .createRecordRouteHeader(addressFactory.createAddress(recordRouteUri));
                forwarded.addHeader(recordRoute);
            }

            ViaHeader via = headerFactory.createViaHeader(myAddress, port, transport, null);
            forwarded.addFirst(via);

            ClientTransaction ct = sipProvider.getNewClientTransaction(forwarded);
            ct.setApplicationData(st);
            logger.logInfo("proxy:" + port + " forwarding " + request.getMethod());
            ct.sendRequest();
        } catch (Exception ex) {
            logger.logError("proxy: could not forward request", ex);
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
            logger.logError("proxy: could not forward response", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
        logger.logInfo("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.logError("proxy: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.logInfo("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.logInfo("dialog terminated event recieved:" + e);
    }

    public void stop() {
        sipStack.stop();
    }
}
