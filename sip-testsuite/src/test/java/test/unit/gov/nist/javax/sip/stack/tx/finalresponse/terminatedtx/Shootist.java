package test.unit.gov.nist.javax.sip.stack.tx.finalresponse.terminatedtx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.Utils;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Sends the REGISTER statelessly: no client transaction to swallow a second final,
 * every final on the wire reaches the listener.
 */
public class Shootist implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int peerPort;
    private final String transport;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private volatile String registerBranch;
    /** "status branch" of every final to the REGISTER, one entry per final received */
    private final List<String> registerFinals = Collections.synchronizedList(new ArrayList<String>());

    public Shootist(int port, int peerPort, String transport) {
        this.port = port;
        this.peerPort = peerPort;
        this.transport = transport;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", transport, false, false,
                false);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootist", ex);
        }
    }

    public void sendRegister() {
        try {
            SipURI aor = addressFactory.createSipURI("caller", "register.test");
            FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(aor),
                    Utils.getInstance().generateTag());
            ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(aor), null);

            SipURI requestUri = addressFactory.createSipURI(null, myAddress);
            requestUri.setPort(peerPort);
            requestUri.setTransportParam(transport);

            registerBranch = Utils.getInstance().generateBranchId();
            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            // stateless send: the branch is ours to set
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, transport, registerBranch));

            Request request = messageFactory.createRequest(requestUri, Request.REGISTER, sipProvider.getNewCallId(),
                    headerFactory.createCSeqHeader(1L, Request.REGISTER), fromHeader, toHeader, viaHeaders,
                    headerFactory.createMaxForwardsHeader(70));
            SipURI contactUri = addressFactory.createSipURI("caller", myAddress);
            contactUri.setPort(port);
            contactUri.setTransportParam(transport);
            request.addHeader(headerFactory.createContactHeader(addressFactory.createAddress(contactUri)));
            request.addHeader(headerFactory.createExpiresHeader(30));

            logger.info("shootist:" + port + " sending REGISTER branch=" + registerBranch);
            sipProvider.sendRequest(request);
        } catch (Exception ex) {
            throw new RuntimeException("could not send REGISTER", ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        String branch = ((ViaHeader) response.getHeader(ViaHeader.NAME)).getBranch();
        logger.info("shootist:" + port + " received " + response.getStatusCode() + " " + cseq.getMethod() + " branch="
                + branch);
        if (response.getStatusCode() >= 200 && Request.REGISTER.equals(cseq.getMethod())) {
            registerFinals.add(response.getStatusCode() + " " + branch);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.info("shootist:" + port + " unexpected request " + requestEvent.getRequest().getMethod());
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

    public String getRegisterBranch() {
        return registerBranch;
    }

    public List<String> getRegisterFinals() {
        synchronized (registerFinals) {
            return new ArrayList<String>(registerFinals);
        }
    }

    public void stop() {
        sipStack.stop();
    }
}
