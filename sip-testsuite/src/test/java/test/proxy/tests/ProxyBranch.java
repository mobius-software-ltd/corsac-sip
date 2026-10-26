package test.proxy.tests;

import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.TransactionState;
import javax.sip.address.SipURI;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One proxy branch. Server transaction is stored as 
 * client transaction application data 
 */
public class ProxyBranch {

    private static final Logger logger = LogManager.getLogger(ProxyBranch.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final ProxyUa proxy;
    private final ServerTransaction serverTransaction;
    private final Request originalRequest;
    private final int targetPort;

    private ClientTransaction clientTransaction;

    public ProxyBranch(ProxyUa proxy, ServerTransaction serverTransaction, Request originalRequest,
            int targetPort) {
        this.proxy = proxy;
        this.serverTransaction = serverTransaction;
        this.originalRequest = originalRequest;
        this.targetPort = targetPort;
    }

    public void forward() throws Exception {
        Request newRequest = (Request) originalRequest.clone();

        RouteHeader topRoute = (RouteHeader) newRequest.getHeader(RouteHeader.NAME);
        if (topRoute != null && topRoute.getAddress().getURI() instanceof SipURI
                && ((SipURI) topRoute.getAddress().getURI()).getPort() == proxy.getPort()) {
            newRequest.removeFirst(RouteHeader.NAME);
        }

        SipURI routeUri = proxy.addressFactory.createSipURI(null, myAddress);
        routeUri.setPort(targetPort);
        routeUri.setLrParam();
        newRequest.addFirst(proxy.headerFactory.createRouteHeader(proxy.addressFactory.createAddress(routeUri)));

        ViaHeader via = proxy.headerFactory.createViaHeader(myAddress, proxy.getPort(), transport, null);
        newRequest.addFirst(via);

        SipURI recordRouteUri = proxy.addressFactory.createSipURI(null, myAddress);
        recordRouteUri.setPort(proxy.getPort());
        recordRouteUri.setLrParam();
        RecordRouteHeader recordRoute = proxy.headerFactory
                .createRecordRouteHeader(proxy.addressFactory.createAddress(recordRouteUri));
        newRequest.addHeader(recordRoute);

        clientTransaction = proxy.sipProvider.getNewClientTransaction(newRequest);
        clientTransaction.setApplicationData(serverTransaction);
        logger.info("proxy branch: forwarding " + originalRequest.getMethod() + " to port " + targetPort);
        clientTransaction.sendRequest();
    }

    /** Dead branches are skipped. */
    public void cancel() throws Exception {
        if (clientTransaction != null && clientTransaction.getState() == TransactionState.PROCEEDING) {
            Request cancel = clientTransaction.createCancel();
            proxy.sipProvider.getNewClientTransaction(cancel).sendRequest();
        }
    }

    public ClientTransaction getClientTransaction() {
        return clientTransaction;
    }

    public int getTargetPort() {
        return targetPort;
    }
}
