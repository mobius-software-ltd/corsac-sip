package test.proxy.tests;

import java.util.concurrent.ConcurrentLinkedQueue;

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


public class ProxyUa implements SipListener {

    private static final Logger logger = LogManager.getLogger(ProxyUa.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";
    private static final long POLL_INTERVAL_MS = 50;

    private final int port;

    final AddressFactory addressFactory;
    final HeaderFactory headerFactory;
    final MessageFactory messageFactory;
    private final SipStack sipStack;
    SipProvider sipProvider;

    private final ConcurrentLinkedQueue<RequestEvent> requestEvents = new ConcurrentLinkedQueue<RequestEvent>();
    private final ConcurrentLinkedQueue<ResponseEvent> responseEvents = new ConcurrentLinkedQueue<ResponseEvent>();

    public ProxyUa(int port) {
        this.port = port;
        ProtocolObjects protocolObjects = new ProtocolObjects("proxyua-" + port, "gov.nist", transport, false,
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
            throw new RuntimeException("could not create proxy ua", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.info("proxy ua: queueing request " + requestEvent.getRequest().getMethod());
        requestEvents.offer(requestEvent);
    }

    public void processResponse(ResponseEvent responseEvent) {
        logger.info("proxy ua: queueing response " + responseEvent.getResponse().getStatusCode());
        responseEvents.offer(responseEvent);
    }

    public RequestEvent pollRequest(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            RequestEvent event = requestEvents.poll();
            if (event != null) {
                return event;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return null;
    }

    public ResponseEvent pollResponseAbove100(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ResponseEvent event = responseEvents.poll();
            if (event != null) {
                if (event.getResponse().getStatusCode() == Response.TRYING) {
                    continue;
                }
                return event;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return null;
    }

    public ServerTransaction ensureTransaction(RequestEvent requestEvent) throws Exception {
        ServerTransaction st = requestEvent.getServerTransaction();
        if (st == null) {
            st = sipProvider.getNewServerTransaction(requestEvent.getRequest());
        }
        return st;
    }

    public void relayResponse(ResponseEvent responseEvent) throws Exception {
        Response newResponse = (Response) responseEvent.getResponse().clone();
        newResponse.removeFirst(ViaHeader.NAME);
        ClientTransaction ct = responseEvent.getClientTransaction();
        ServerTransaction st = ct == null ? null : (ServerTransaction) ct.getApplicationData();
        if (st != null) {
            st.sendResponse(newResponse);
        } else {
            sipProvider.sendResponse(newResponse);
        }
    }

    /** Forward an in-dialog request statelessly after consuming our Route. */
    public void forwardInDialog(Request request) throws Exception {
        Request newRequest = (Request) request.clone();
        RouteHeader topRoute = (RouteHeader) newRequest.getHeader(RouteHeader.NAME);
        if (topRoute != null && topRoute.getAddress().getURI() instanceof SipURI
                && ((SipURI) topRoute.getAddress().getURI()).getPort() == port) {
            newRequest.removeFirst(RouteHeader.NAME);
        }
        logger.info("proxy ua: forwarding in-dialog " + request.getMethod());
        sipProvider.sendRequest(newRequest);
    }

    public int getPort() {
        return port;
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("proxy ua: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public void stop() {
        sipStack.stop();
    }
}
