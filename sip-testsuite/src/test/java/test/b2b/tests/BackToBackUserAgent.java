package test.b2b.tests;

import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
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
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.SipProviderExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.HeaderFactoryExt;
import gov.nist.javax.sip.message.ResponseExt;
import test.tck.msgflow.callflows.ProtocolObjects;

public class BackToBackUserAgent implements SipListener {

    private static final Logger logger = LogManager.getLogger(BackToBackUserAgent.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final ListeningPoint[] listeningPoints = new ListeningPoint[2];
    private final SipProvider[] providers = new SipProvider[2];
    private final MessageFactory messageFactory;
    private final HeaderFactoryExt headerFactory;
    private final SipStack sipStack;
    private final Hashtable<Dialog, Response> lastResponseTable = new Hashtable<Dialog, Response>();
    private final ConcurrentHashMap<Dialog, Dialog> linkedDialogs = new ConcurrentHashMap<Dialog, Dialog>();

    private final int targetPort;
    public boolean acceptOneForkedResponse;
    private volatile int forkedDialogsTerminated;
    private volatile int relayedFinalResponses;
    private volatile int unlinkedRequestsRejected;
    private volatile int keepAlivesAnswered;

    public BackToBackUserAgent(int port1, int port2, int targetPort) {
        this.targetPort = targetPort;
        ProtocolObjects protocolObjects = new ProtocolObjects("backtobackua-" + port1, "gov.nist", transport, true,
                true, false);
        this.messageFactory = protocolObjects.messageFactory;
        this.headerFactory = (HeaderFactoryExt) protocolObjects.headerFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ((SipStackImpl) sipStack).setMaxForkTime(32);
            ListeningPoint lp1 = sipStack.createListeningPoint(myAddress, port1, transport);
            ListeningPoint lp2 = sipStack.createListeningPoint(myAddress, port2, transport);
            SipProvider sp1 = sipStack.createSipProvider(lp1);
            SipProvider sp2 = sipStack.createSipProvider(lp2);
            listeningPoints[0] = lp1;
            listeningPoints[1] = lp2;
            providers[0] = sp1;
            providers[1] = sp2;
            sp1.addSipListener(this);
            sp2.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create call b2bua", ex);
        }
    }


    private SipProvider getPeerProvider(SipProvider provider) {
        return provider == providers[0] ? providers[1] : providers[0];
    }

    private ContactHeader createPeerContact(SipProvider provider) {
        return ((ListeningPointExt) ((SipProviderExt) getPeerProvider(provider))
                .getListeningPoint(transport)).createContactHeader();
    }

    private void linkDialogs(Dialog dialog, Dialog peer) {
        linkedDialogs.put(dialog, peer);
        linkedDialogs.put(peer, dialog);
    }

    private Dialog getLinkedDialog(Dialog dialog) {
        return dialog == null ? null : linkedDialogs.get(dialog);
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        SipProvider provider = (SipProvider) requestEvent.getSource();
        logger.info("b2bua: received " + method);
        try {
            if (method.equals(Request.INVITE) || method.equals(Request.BYE)) {
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = provider.getNewServerTransaction(request);
                }
                String toTag = ((ToHeader) request.getHeader(ToHeader.NAME)).getTag();
                
                // dead link, nowhere to send the request
                if (toTag != null && getLinkedDialog(st.getDialog()) == null) {
                    unlinkedRequestsRejected++;
                    logger.info("b2bua: rejecting " + method + " on unlinked dialog with 481");
                    st.sendResponse(messageFactory
                            .createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, request));
                    return;
                }
                forwardRequest(provider, request, st);
            } else if (method.equals(Request.ACK)) {
                Dialog dialog = requestEvent.getDialog();
                Dialog peer = getLinkedDialog(dialog);
                Response lastResponse = lastResponseTable.get(peer);
                if (lastResponse != null && lastResponse.getStatusCode() == Response.OK) {
                    CSeqHeader cseq = (CSeqHeader) lastResponse.getHeader(CSeqHeader.NAME);
                    peer.sendAck(peer.createAck(cseq.getSeqNumber()));
                }
            } else if (method.equals(Request.OPTIONS)) {
                //  keepAlive handling
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = provider.getNewServerTransaction(request);
                }
                Response pong = messageFactory.createResponse(Response.OK, request);
                ToHeader toHeader = (ToHeader) pong.getHeader(ToHeader.NAME);
                if (toHeader.getTag() == null) {
                    toHeader.setTag(Long.toString(Math.abs(new Random().nextLong())));
                }
                st.sendResponse(pong);
                keepAlivesAnswered++;
            } else if (method.equals(Request.PRACK)) {
                ServerTransaction st = requestEvent.getServerTransaction();
                Dialog dialog = requestEvent.getDialog();
                Dialog peer = getLinkedDialog(dialog);
                Response reliableProvisional = lastResponseTable.get(peer);
                Request prack = peer.createPrack(reliableProvisional);
                ClientTransaction ct = getPeerProvider(provider).getNewClientTransaction(prack);
                ct.setApplicationData(st);
                peer.sendRequest(ct);
            }
        } catch (Exception ex) {
            logger.error("b2bua: Unepxected exception processing " + method, ex);
        }
    }

    private void forwardRequest(SipProvider provider, Request request, ServerTransaction st) throws Exception {
        Dialog dialog = st.getDialog();
        Dialog peerDialog = getLinkedDialog(dialog);
        Request newRequest;
        ClientTransaction ct;
        if (peerDialog != null) {
            newRequest = peerDialog.createRequest(request.getMethod());
            newRequest.setHeader(createPeerContact(provider));
            ct = getPeerProvider(provider).getNewClientTransaction(newRequest);
            ct.setApplicationData(st);
            logger.info("b2bua: forwarding in-dialog " + request.getMethod());
            peerDialog.sendRequest(ct);
        } else {
            newRequest = (Request) request.clone();
            ((SipURI) newRequest.getRequestURI()).setPort(targetPort);
            newRequest.removeHeader(RouteHeader.NAME);
            FromHeader fromHeader = (FromHeader) newRequest.getHeader(FromHeader.NAME);
            fromHeader.setTag(Long.toString(Math.abs(new Random().nextLong())));
            ViaHeader viaHeader = ((ListeningPointExt) ((SipProviderExt) getPeerProvider(provider))
                    .getListeningPoint(transport)).createViaHeader();
            newRequest.setHeader(viaHeader);
            newRequest.setHeader(createPeerContact(provider));
            ct = getPeerProvider(provider).getNewClientTransaction(newRequest);
            ct.setApplicationData(st);
            linkDialogs(dialog, ct.getDialog());
            logger.info("b2bua: forwarding initial " + request.getMethod() + " to port " + targetPort);
            ct.sendRequest();
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
            Response response = responseEvent.getResponse();
            int status = response.getStatusCode();
            if (status == Response.TRYING || responseEventExt.isRetransmission()) {
                return;
            }
            Dialog dialog = responseEvent.getDialog();
            SipProvider provider = (SipProvider) responseEvent.getSource();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            logger.info("b2bua: received response " + status + " for " + cseq.getMethod() + ", forked="
                    + responseEventExt.isForkedResponse());

            if (responseEventExt.isForkedResponse()) {
                if (acceptOneForkedResponse && status / 100 == 2 && cseq.getMethod().equals(Request.INVITE)) {
                    // fork loser 2xx: ACK it and kill the leg
                    Request ack = dialog.createAck(cseq.getSeqNumber());
                    dialog.sendAck(ack);
                    Request bye = dialog.createRequest(Request.BYE);
                    ClientTransaction byeTransaction = provider.getNewClientTransaction(bye);
                    dialog.sendRequest(byeTransaction);
                    forkedDialogsTerminated++;
                    logger.info("b2bua: terminated extra forked dialog " + dialog.getDialogId());
                }
                return;
            }

            if (dialog != null) {
                lastResponseTable.put(dialog, response);
            }
            ClientTransaction ct = responseEvent.getClientTransaction();
            if (ct == null) {
                ct = responseEventExt.getOriginalTransaction();
            }
            if (ct == null || ct.getApplicationData() == null) {
                // response to our own BYE
                return;
            }
            ServerTransaction st = (ServerTransaction) ct.getApplicationData();
            Response newResponse = messageFactory.createResponse(status, st.getRequest());
            String toTag = ((ResponseExt) response).getToHeader().getTag();
            ToHeader newTo = ((ResponseExt) newResponse).getToHeader();
            if (toTag != null && newTo.getTag() == null) {
                newTo.setTag(toTag);
            }
            newResponse.setHeader(createPeerContact(provider));
            if (status == Response.SESSION_PROGRESS && response.getHeader(RequireHeader.NAME) != null) {
                RequireHeader require = headerFactory.createRequireHeader("100rel");
                newResponse.addHeader(require);
                Dialog uasDialog = st.getDialog();
                logger.info("b2bua: relaying reliable provisional upstream");
                uasDialog.sendReliableProvisionalResponse(newResponse);
            } else {
                if (status >= 200) {
                    relayedFinalResponses++;
                }
                st.sendResponse(newResponse);
            }
        } catch (Exception ex) {
            logger.error("b2bua: unexpected exception processing response", ex);
        }
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("b2bua: transaction timeout:" +e);
    }

    public void processIOException(IOExceptionEvent e) {
        logger.error("b2bua: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public int getForkedDialogsTerminated() {
        return forkedDialogsTerminated;
    }

    public int getRelayedFinalResponses() {
        return relayedFinalResponses;
    }

    public int getUnlinkedRequestsRejected() {
        return unlinkedRequestsRejected;
    }

    public int getKeepAlivesAnswered() {
        return keepAlivesAnswered;
    }

    public void unlinkAllDialogs() {
        linkedDialogs.clear();
    }

    public void stop() {
        sipStack.stop();
    }
}
