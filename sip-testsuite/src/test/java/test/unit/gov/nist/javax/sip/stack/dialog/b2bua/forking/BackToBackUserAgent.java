package test.unit.gov.nist.javax.sip.stack.dialog.b2bua.forking;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Random;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipProviderExt;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.message.MessageExt;
import gov.nist.javax.sip.message.ResponseExt;
import test.tck.msgflow.callflows.ProtocolObjects;

public class BackToBackUserAgent implements SipListenerExt {
    private static Logger logger = LogManager.getLogger(BackToBackUserAgent.class);
    // private HashSet<Dialog> dialogs = new HashSet<Dialog>();
    private HashMap<Dialog, Dialog> dialogs = new HashMap<Dialog, Dialog>();
    private ListeningPoint[] listeningPoints = new ListeningPoint[2];
    private SipProvider[] providers = new SipProvider[2];
    private MessageFactory messageFactory;
    private Hashtable<Dialog, Response> lastResponseTable = new Hashtable<Dialog, Response>();
    private ProtocolObjects protocolObjects;

    public Dialog getPeerDialog(Dialog dialog) {
        return this.dialogs.get(dialog);
    }

    // public Dialog getPeer(Dialog dialog) {
    //     Object[] dialogArray = dialogs.toArray();
    //     if (dialogArray.length < 2)
    //         return null;
    //     if (dialogArray[0] == dialog)
    //         return (Dialog) dialogArray[1];
    //     else if (dialogArray[1] == dialog)
    //         return (Dialog) dialogArray[0];
    //     else
    //         return null;
    // }

    public SipProvider getPeerProvider(SipProvider provider) {
        if (providers[0] == provider)
            return providers[1];
        else
            return providers[0];
    }

    public void addDialog(Dialog uacDialog, Dialog uasDialog) {
        this.dialogs.put(uacDialog, uasDialog);
        this.dialogs.put(uasDialog, uacDialog);
        System.out.println("Dialogs  " + this.dialogs);
    }

    public void forwardRequest(RequestEvent requestEvent,
            ServerTransaction serverTransaction) throws SipException, ParseException, InvalidArgumentException {

        SipProvider provider = (SipProvider) requestEvent.getSource();
        Dialog dialog = serverTransaction.getDialog();
        Dialog peerDialog = this.getPeerDialog(dialog);
        Request request = requestEvent.getRequest();

        System.out.println("UAS Dialog " + dialog + ", peerDialog " + peerDialog );

        Request newRequest = null;
        if (peerDialog != null) {
            newRequest = peerDialog.createRequest(request.getMethod());
            ContactHeader contactHeader = ((ListeningPointExt) ((SipProviderExt) getPeerProvider(provider))
                    .getListeningPoint("udp")).createContactHeader();
            newRequest.setHeader(contactHeader);
            ClientTransaction clientTransaction = provider.getNewClientTransaction(newRequest);
            clientTransaction.setApplicationData(serverTransaction);
            // if (request.getMethod().equals(Request.INVITE)) {
            //     this.addDialog(clientTransaction.getDialog());
            // }
            peerDialog.sendRequest(clientTransaction);
        } else {
            // Forwarding request to multiple targets
            for (int targetPort : targetPorts) {
                newRequest = (Request) request.clone();
                ((SipURI) newRequest.getRequestURI()).setPort(targetPort);
                newRequest.removeHeader(RouteHeader.NAME);
                FromHeader fromHeader = (FromHeader) newRequest.getHeader(FromHeader.NAME);
                fromHeader.setTag(Long.toString(Math.abs(new Random().nextLong())));
                ViaHeader viaHeader = ((ListeningPointExt) ((SipProviderExt) getPeerProvider(provider))
                        .getListeningPoint("udp")).createViaHeader();
                newRequest.setHeader(viaHeader);
                ContactHeader contactHeader = ((ListeningPointExt) ((SipProviderExt) getPeerProvider(provider))
                        .getListeningPoint("udp")).createContactHeader();
                newRequest.setHeader(contactHeader);
                ClientTransaction clientTransaction = provider.getNewClientTransaction(newRequest);
                clientTransaction.setApplicationData(serverTransaction);
                if (request.getMethod().equals(Request.INVITE)) {
                    Dialog uacDialog = clientTransaction.getDialog();
                    if(uacDialog == null) {
                        uacDialog = provider.getNewDialog(clientTransaction);                
                    }                    
                    System.out.println("UAC Dialog " + uacDialog);
                }                
                clientTransaction.sendRequest();
            }

        }        
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {

    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {

    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        // TODO Auto-generated method stub

    }

    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            SipProvider provider = (SipProvider) requestEvent.getSource();
            if (request.getMethod().equals(Request.INVITE)) {
                if (requestEvent.getServerTransaction() == null) {
                    try {
                        ServerTransaction serverTx = provider.getNewServerTransaction(request);                        
                        this.forwardRequest(requestEvent, serverTx);
                    } catch (TransactionAlreadyExistsException ex) {
                        System.err.println("Transaction exists -- ignoring");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        BackToBackUserAgentTest.fail("Unepxected exception");
                    }
                } else {
                    this.forwardRequest(requestEvent, requestEvent.getServerTransaction());
                }
            } else if (request.getMethod().equals(Request.BYE)) {
                ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                if (serverTransaction == null) {
                    serverTransaction = provider.getNewServerTransaction(request);
                }
                this.forwardRequest(requestEvent, serverTransaction);

            } else if (request.getMethod().equals(Request.ACK)) {
                Dialog dialog = requestEvent.getDialog();
                System.out.println("ACK Dialog " + dialog);
                Dialog peer = this.getPeerDialog(dialog);
                System.out.println("ACK Peer Dialog " + peer);
                Response response = this.lastResponseTable.get(peer);
                CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                long seqno = cseqHeader.getSeqNumber();
                Request ack = peer.createAck(seqno);
                peer.sendAck(ack);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            BackToBackUserAgentTest.fail("Unexpected exception forwarding request");
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            Dialog dialog = responseEvent.getDialog();
            logger.info("B2BUA - response: " + response + " received for dialog: " + dialog);
            if(response.getStatusCode() == Response.TRYING) {
                logger.info("Dropping Trying responses");
                return;
            }
            this.lastResponseTable.put(dialog, response);
            ServerTransaction serverTransaction = (ServerTransaction) responseEvent.getClientTransaction()
                    .getApplicationData();
            Request stRequest = serverTransaction.getRequest();
            Response newResponse = this.messageFactory.createResponse(response.getStatusCode(), stRequest);
            SipProvider provider = (SipProvider) responseEvent.getSource();
            SipProvider peerProvider = this.getPeerProvider(provider);
            ListeningPoint peerListeningPoint = peerProvider.getListeningPoint("udp");
            ContactHeader peerContactHeader = ((ListeningPointExt) peerListeningPoint).createContactHeader();
            newResponse.setHeader(peerContactHeader);
            ((ResponseExt)newResponse).getToHeader().setTag(((ResponseExt)response).getToHeader().getTag());            
            Dialog uasDialog = null;
            if(((ResponseExt)newResponse).getCSeqHeader().getMethod() == Request.INVITE) {                
                uasDialog = peerProvider.getNewDialog(serverTransaction);
                this.addDialog(dialog, uasDialog);
            } else {
                uasDialog = this.getPeerDialog(dialog);
            }                   
            logger.info("B2BUA - newResponse: " + newResponse + " sent for UAC Dialog " + dialog + " and UAS dialog: " + uasDialog);
            serverTransaction.sendResponse(newResponse);            
        } catch (Exception ex) {
            ex.printStackTrace();
            BackToBackUserAgentTest.fail("Unexpected exception");
        }
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        // TODO Auto-generated method stub

    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {

    }

    public BackToBackUserAgent(int port1, int port2) {
        SipFactory sipFactory = null;
        sipFactory = SipFactory.getInstance();
        sipFactory.resetFactory();
        sipFactory.setPathName("gov.nist");
        this.protocolObjects = new ProtocolObjects("backtobackua", "gov.nist", "udp", false, true, false);

        try {
            messageFactory = protocolObjects.messageFactory;
            SipStack sipStack = protocolObjects.sipStack;
            ListeningPoint lp1 = sipStack.createListeningPoint("127.0.0.1", port1, "udp");
            ListeningPoint lp2 = sipStack.createListeningPoint("127.0.0.1", port2, "udp");
            SipProvider sp1 = sipStack.createSipProvider(lp1);
            SipProvider sp2 = sipStack.createSipProvider(lp2);
            this.listeningPoints[0] = lp1;
            this.listeningPoints[1] = lp2;
            this.providers[0] = sp1;
            this.providers[1] = sp2;
            sp1.addSipListener(this);
            sp2.addSipListener(this);
        } catch (Exception ex) {

        }

    }

    private ArrayList<Integer> targetPorts = new ArrayList<>();

    public int getTargetPort(int index) {
        return targetPorts.get(index).intValue();
    }

    public void addTargetPort(int targetPort) {
        this.targetPorts.add(Integer.valueOf(targetPort));
    }

    @Override
    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
        logger.info("message Sent " + messageSentEvent + " transaction " + transaction);
    }

}