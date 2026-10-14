package test.unit.gov.nist.javax.sip.stack.udpnio;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
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
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;

import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * INVITE, ACK on the 200, BYE when the test asks for it; every request here is one
 * the UDP stack originates itself.
 */
public class Shootist implements SipListener {

    private static final StackLogger logger = CommonLogger.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int peerPort;

    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private volatile Dialog dialog;
    private volatile boolean inviteOkSeen;
    private volatile boolean byeOkSeen;
    private volatile boolean timeoutSeen;

    public Shootist(int port, int peerPort, Properties stackProperties) {
        this.port = port;
        this.peerPort = peerPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootist-" + port, "gov.nist", ListeningPoint.UDP,
                true, false, false, stackProperties);
        this.addressFactory = protocolObjects.addressFactory;
        this.headerFactory = protocolObjects.headerFactory;
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, ListeningPoint.UDP);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootist", ex);
        }
    }

    public void sendInvite() {
        try {
            SipURI fromUri = addressFactory.createSipURI("caller", "udpnio.test");
            FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromUri),
                    "shootist-" + port);
            SipURI toUri = addressFactory.createSipURI("callee", "udpnio.test");
            ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(toUri), null);

            SipURI requestUri = addressFactory.createSipURI("callee", myAddress);
            requestUri.setPort(peerPort);
            requestUri.setTransportParam(ListeningPoint.UDP);

            List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            viaHeaders.add(headerFactory.createViaHeader(myAddress, port, ListeningPoint.UDP, null));
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards);
            request.addHeader(createContact());

            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            dialog = clientTransaction.getDialog();
            logger.logInfo("shootist: sending INVITE to " + peerPort);
            clientTransaction.sendRequest();
        } catch (Exception ex) {
            throw new RuntimeException("could not send INVITE", ex);
        }
    }

    public void sendBye() {
        try {
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(dialog.createRequest(Request.BYE));
            logger.logInfo("shootist: sending BYE");
            dialog.sendRequest(clientTransaction);
        } catch (Exception ex) {
            throw new RuntimeException("could not send BYE", ex);
        }
    }

    private ContactHeader createContact() throws Exception {
        SipURI contactUri = addressFactory.createSipURI("caller", myAddress);
        contactUri.setPort(port);
        contactUri.setTransportParam(ListeningPoint.UDP);
        return headerFactory.createContactHeader(addressFactory.createAddress(contactUri));
    }

    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        logger.logInfo("shootist:" + port + " received " + response.getStatusCode() + " " + cseq.getMethod());
        if (response.getStatusCode() != Response.OK) {
            return;
        }
        try {
            if (cseq.getMethod().equals(Request.INVITE)) {
                inviteOkSeen = true;
                Dialog dialog = responseEvent.getDialog();
                dialog.sendAck(dialog.createAck(cseq.getSeqNumber()));
            } else if (cseq.getMethod().equals(Request.BYE)) {
                byeOkSeen = true;
            }
        } catch (Exception ex) {
            logger.logError("shootist: could not ACK", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        logger.logInfo("shootist:" + port + " received " + requestEvent.getRequest().getMethod());
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        timeoutSeen = true;
        logger.logInfo("shootist: transaction timeout " + timeoutEvent.getTimeout());
    }

    public void processIOException(IOExceptionEvent e) {
        logger.logError("shootist: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.logInfo("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.logInfo("dialog terminated event recieved:" + e);
    }

    public boolean isTimeoutSeen() {
        return timeoutSeen;
    }

    public TestAssertion getInviteOkAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return inviteOkSeen;
            }
        };
    }

    public TestAssertion getByeOkAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return byeOkSeen;
            }
        };
    }

    public void stop() {
        sipStack.stop();
    }
}
