package test.proxy.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import test.tck.msgflow.callflows.ProtocolObjects;

public class Registrar implements SipListener {

    private static final Logger logger = LogManager.getLogger(Registrar.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;
    private final String realm;
    private final String password;

    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;
    private DigestServerAuthenticationHelper authenticationHelper;

    private volatile boolean challengeSent;
    private volatile boolean authenticationAccepted;
    private final List<String> registeredPaths = new ArrayList<String>();
    private final ConcurrentHashMap<String, List<SipURI>> bindings = new ConcurrentHashMap<String, List<SipURI>>();

    public Registrar(int port, String realm, String password) {
        this.port = port;
        this.realm = realm;
        this.password = password;
        ProtocolObjects protocolObjects = new ProtocolObjects("registrar-" + port, "gov.nist", transport, false,
                false, false);
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            this.authenticationHelper = new DigestServerAuthenticationHelper();
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create registrar", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        logger.info("registrar: received " + request.getMethod());
        try {
            if (!request.getMethod().equals(Request.REGISTER)) {
                return;
            }
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            if (authenticationHelper.doAuthenticatePlainTextPassword(request, password)) {
                authenticationAccepted = true;
                Response ok = messageFactory.createResponse(Response.OK, request);
                ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
                if (contact != null) {
                    ok.addHeader((Header) contact.clone());
                    if (contact.getAddress().getURI() instanceof SipURI) {
                        ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
                        String user = ((SipURI) toHeader.getAddress().getURI()).getUser();
                        List<SipURI> userBindings = bindings.get(user);
                        if (userBindings == null) {
                            userBindings = Collections.synchronizedList(new ArrayList<SipURI>());
                            bindings.put(user, userBindings);
                        }
                        userBindings.add((SipURI) contact.getAddress().getURI().clone());
                        logger.info("registrar: stored binding for " + user + ": " + userBindings);
                    }
                }
                // echo PATH back per RFC 3327
                for (Iterator<?> paths = request.getHeaders("Path"); paths.hasNext();) {
                    Header path = (Header) paths.next();
                    registeredPaths.add(path.toString().trim());
                    ok.addHeader((Header) path.clone());
                }
                logger.info("registrar: authenticated REGISTER, paths=" + registeredPaths);
                st.sendResponse(ok);
            } else {
                challengeSent = true;
                Response challenge = messageFactory.createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, request);
                authenticationHelper.generateChallenge(headerFactory, challenge, realm);
                logger.info("registrar: challenging REGISTER with 407");
                st.sendResponse(challenge);
            }
        } catch (Exception ex) {
            logger.error("registrar: unexpected exception", ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
    }

    public void processTimeout(TimeoutEvent e) {
    	logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("registrar: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public boolean isChallengeSent() {
        return challengeSent;
    }

    public boolean isAuthenticationAccepted() {
        return authenticationAccepted;
    }

    public List<String> getRegisteredPaths() {
        return registeredPaths;
    }

    public List<SipURI> getBindings(String user) {
        List<SipURI> userBindings = bindings.get(user);
        return userBindings == null ? new ArrayList<SipURI>() : userBindings;
    }

    public void stop() {
        sipStack.stop();
    }
}
