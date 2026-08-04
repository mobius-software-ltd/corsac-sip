package test.proxy.tests;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
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
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import test.tck.msgflow.callflows.ProtocolObjects;

public class Registree implements SipListener {

    private static final Logger logger = LogManager.getLogger(Registree.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final int edgeProxyPort;
    private final String username;
    private final String realmPassword;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private CallIdHeader callIdHeader;
    private String fromTag;
    private long cseq = 1;
    public int contactPort = -1;

    private volatile boolean challengeSeen;
    private volatile boolean registered;
    private final List<String> pathsInFinalResponse = new ArrayList<String>();

    public Registree(int port, int edgeProxyPort, String username, String password) {
        this.port = port;
        this.edgeProxyPort = edgeProxyPort;
        this.username = username;
        this.realmPassword = password;
        ProtocolObjects protocolObjects = new ProtocolObjects("registree-" + port, "gov.nist", transport, false,
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
            throw new RuntimeException("could not create registree", ex);
        }
    }


    public void sendRegister() {
        try {
            callIdHeader = sipProvider.getNewCallId();
            fromTag = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
            sendRegister(null);
        } catch (Exception ex) {
            throw new RuntimeException("could not send REGISTER", ex);
        }
    }

    private void sendRegister(ProxyAuthorizationHeader authorization) throws Exception {
        SipURI aor = addressFactory.createSipURI(username, "test.mobius.local");
        Address aorAddress = addressFactory.createAddress(aor);
        FromHeader fromHeader = headerFactory.createFromHeader(aorAddress, fromTag);
        ToHeader toHeader = headerFactory.createToHeader(aorAddress, null);

        SipURI requestUri = addressFactory.createSipURI(null, "test.mobius.local");

        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, null));

        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq++, Request.REGISTER);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        Request request = messageFactory.createRequest(requestUri, Request.REGISTER, callIdHeader, cSeqHeader,
                fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = addressFactory.createSipURI(username, myAddress);
        contactUri.setPort(contactPort > 0 ? contactPort : port);
        contactUri.setTransportParam(transport);
        ContactHeader contactHeader = headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
        request.addHeader(contactHeader);
        request.addHeader(headerFactory.createExpiresHeader(3600));
        request.addHeader(headerFactory.createSupportedHeader("path"));

        SipURI routeUri = addressFactory.createSipURI(null, myAddress);
        routeUri.setPort(edgeProxyPort);
        routeUri.setLrParam();
        RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeUri));
        request.setHeader(routeHeader);

        if (authorization != null) {
            request.setHeader(authorization);
        }

        ClientTransaction registerTransaction = sipProvider.getNewClientTransaction(request);
        logger.info("registree: sending REGISTER" + (authorization != null ? " with credentials" : ""));
        registerTransaction.sendRequest();
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        logger.info("registree: received " + status);
        try {
            if (status == Response.PROXY_AUTHENTICATION_REQUIRED) {
                challengeSeen = true;
                ProxyAuthenticateHeader challenge = (ProxyAuthenticateHeader) response
                        .getHeader(ProxyAuthenticateHeader.NAME);
                sendRegister(createAuthorization(challenge));
            } else if (status == Response.OK) {
                registered = true;
                for (java.util.Iterator<?> paths = response.getHeaders("Path"); paths.hasNext();) {
                    pathsInFinalResponse.add(((Header) paths.next()).toString().trim());
                }
            }
        } catch (Exception ex) {
            logger.error("registree: unexpected exception processing " + status, ex);
        }
    }

    private ProxyAuthorizationHeader createAuthorization(ProxyAuthenticateHeader challenge) throws Exception {
        String realm = challenge.getRealm();
        String nonce = challenge.getNonce();
        SipURI digestUri = addressFactory.createSipURI(null, "test.mobius.local");

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        String ha1 = DigestServerAuthenticationHelper
                .toHexString(md5.digest((username + ":" + realm + ":" + realmPassword).getBytes()));
        String ha2 = DigestServerAuthenticationHelper
                .toHexString(md5.digest((Request.REGISTER + ":" + digestUri.toString()).getBytes()));
        String digestResponse = DigestServerAuthenticationHelper
                .toHexString(md5.digest((ha1 + ":" + nonce + ":" + ha2).getBytes()));

        ProxyAuthorizationHeader authorization = headerFactory.createProxyAuthorizationHeader("Digest");
        authorization.setUsername(username);
        authorization.setRealm(realm);
        authorization.setNonce(nonce);
        authorization.setURI(digestUri);
        authorization.setAlgorithm("MD5");
        authorization.setResponse(digestResponse);
        return authorization;
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.info("registree: unexpected request " + requestEvent.getRequest().getMethod());
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("registree: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isChallengeSeen() {
        return challengeSeen;
    }

    public boolean isRegistered() {
        return registered;
    }

    public List<String> getPathsInFinalResponse() {
        return pathsInFinalResponse;
    }

    public void stop() {
        sipStack.stop();
    }
}
