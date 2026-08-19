package test.proxy.tests;

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
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * Forwards REGISTER to the registrar, deposits a Path header on the way.
 */
public class EdgeProxy implements SipListener {

    private static final Logger logger = LogManager.getLogger(EdgeProxy.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final int registrarPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private volatile int registersForwarded;
    private volatile int responsesRelayed;

    public EdgeProxy(int port, int registrarPort) {
        this.port = port;
        this.registrarPort = registrarPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("edgeproxy-" + port, "gov.nist", transport, false,
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
            throw new RuntimeException("could not create edge proxy", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        logger.info("edge proxy: received " + request.getMethod());
        try {
            if (!request.getMethod().equals(Request.REGISTER)) {
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            Request newRequest = (Request) request.clone();
            // consume the route pointing at us
            RouteHeader topRoute = (RouteHeader) newRequest.getHeader(RouteHeader.NAME);
            if (topRoute != null) {
                newRequest.removeFirst(RouteHeader.NAME);
            }
            // steer to the registrar
            SipURI registrarUri = addressFactory.createSipURI(null, myAddress);
            registrarUri.setPort(registrarPort);
            registrarUri.setLrParam();
            newRequest.addFirst(headerFactory.createRouteHeader(addressFactory.createAddress(registrarUri)));

            ViaHeader via = headerFactory.createViaHeader(myAddress, port, transport, null);
            newRequest.addFirst(via);

            // PATH per RFC 3327
            newRequest.addHeader(headerFactory.createHeader("Path", "<sip:" + myAddress + ":" + port + ";lr>"));

            ClientTransaction ct = sipProvider.getNewClientTransaction(newRequest);
            ct.setApplicationData(st);
            registersForwarded++;
            ct.sendRequest();
        } catch (Exception ex) {
            logger.error("edge proxy: unexpected exception", ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            logger.info("edge proxy: relaying response " + response.getStatusCode());
            ClientTransaction ct = responseEvent.getClientTransaction();
            if (ct == null || ct.getApplicationData() == null) {
                return;
            }
            Response newResponse = (Response) response.clone();
            newResponse.removeFirst(ViaHeader.NAME);
            ((ServerTransaction) ct.getApplicationData()).sendResponse(newResponse);
            responsesRelayed++;
        } catch (Exception ex) {
            logger.error("edge proxy: unexpected exception relaying response", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("edge proxy: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public int getRegistersForwarded() {
        return registersForwarded;
    }

    public int getResponsesRelayed() {
        return responsesRelayed;
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        sipStack.stop();
    }
}
