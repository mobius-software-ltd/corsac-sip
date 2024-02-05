package test.unit.gov.nist.javax.sip.stack.dialog.b2bua.forking;

import java.util.Hashtable;
import java.util.Iterator;

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
import javax.sip.header.CSeqHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * A very simple forking proxy server.
 * 
 * @author M. Ranganathan
 * 
 */
public class Proxy implements SipListener {

    // private ServerTransaction st;

    private SipProvider inviteServerTxProvider;

    private Hashtable<Integer,ClientTransaction> clientTxTable = new Hashtable<Integer,ClientTransaction>();

    private ServerTransaction inviteServerTransaction;
    private ServerTransaction cancelServerTransaction;

    private static String host = "127.0.0.1";

    private int port = 5070;

    private SipProvider sipProvider;

    private static String unexpectedException = "Unexpected exception";

    private static Logger logger = LogManager.getLogger(Proxy.class);

    private static AddressFactory addressFactory;

    private static HeaderFactory headerFactory;

    private static String transport = "udp";

    private SipStack sipStack;

    private int ntargets;
    
    
    private void sendTo(ServerTransaction serverTransaction, Request request, int targetPort) throws Exception {
        Request newRequest = (Request) request.clone();
        
        SipURI sipUri = addressFactory.createSipURI("UA1", "127.0.0.1");
        sipUri.setPort(targetPort);
        sipUri.setLrParam();
        Address address = addressFactory.createAddress("client1", sipUri);
        RouteHeader rheader = headerFactory.createRouteHeader(address);

        newRequest.addFirst(rheader);
        String branch = null;
        if (request.getMethod().equals(Request.CANCEL)) {
            branch = ((ClientTransaction)clientTxTable.get(targetPort)).getBranchId();
        }
        ViaHeader viaHeader = headerFactory.createViaHeader(host, this.port, transport, branch);
        newRequest.addFirst(viaHeader);

        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(newRequest);
        clientTransaction.setApplicationData(serverTransaction);
        clientTxTable.put(Integer.valueOf(targetPort), clientTransaction);

        sipUri = addressFactory.createSipURI("proxy", "127.0.0.1");
        address = addressFactory.createAddress("proxy", sipUri);
        sipUri.setPort(port);
        sipUri.setLrParam();
        RecordRouteHeader recordRoute = headerFactory.createRecordRouteHeader(address);
        newRequest.addHeader(recordRoute);
        
        logger.info("proxy: Request to forward " + newRequest);
        
        clientTransaction.sendRequest();
    }

    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            this.inviteServerTxProvider = sipProvider;
            if (request.getMethod().equals(Request.INVITE) || request.getMethod().equals(Request.CANCEL) ) {
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = sipProvider.getNewServerTransaction(request);
                    inviteServerTransaction = st;
                } else if (request.getMethod().equals(Request.CANCEL)) {    
                    st = requestEvent.getServerTransaction();
                    cancelServerTransaction = st;
                }                
                for ( int i = 0; i < ntargets; i++ ) {
                    this.sendTo(st,request,targetPorts[i]);
                }
            } else {
                // Remove the topmost route header
                // The route header will make sure it gets to the right place.
                logger.info("proxy: Got a request " + request.getMethod());
                Request newRequest = (Request) request.clone();
                newRequest.removeFirst(RouteHeader.NAME);
                sipProvider.sendRequest(newRequest);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");
        }

    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            logger.info("ClientTxID = " + responseEvent.getClientTransaction() + " client tx id "
                    + ((ViaHeader) response.getHeader(ViaHeader.NAME)).getBranch()
                    + " CSeq header = " + response.getHeader(CSeqHeader.NAME) + " status code = "
                    + response.getStatusCode());

            // JvB: stateful proxy MUST NOT forward 100 Trying
            if (response.getStatusCode() == 100)
                return;

            if (cseq.getMethod().equals(Request.INVITE) || cseq.getMethod().equals(Request.CANCEL)) {
                ClientTransaction ct = responseEvent.getClientTransaction();
                if (ct != null) {
                    // Strip the topmost via header
                    Response newResponse = (Response) response.clone();
                    newResponse.removeFirst(ViaHeader.NAME);
                    // The server tx goes to the terminated state.
                    ServerTransaction st = (ServerTransaction) ct.getApplicationData();
                    if(st == null) {
                        if(cseq.getMethod().equals(Request.INVITE)) {
                            st = inviteServerTransaction;
                        } else {
                            st = cancelServerTransaction;
                        }
                    }
                    st.sendResponse(newResponse);
                } else {
                    // Client tx has already terminated but the UA is
                    // retransmitting
                    // just forward the response statelessly.
                    // Strip the topmost via header

                    Response newResponse = (Response) response.clone();
                    newResponse.removeFirst(ViaHeader.NAME);
                    // Send the retransmission statelessly
                    this.inviteServerTxProvider.sendResponse(newResponse);
                }
            } else {
                // this is the OK for the cancel.
                logger.info("Got a non-invite response " + response);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            TestCase.fail("unexpected exception");
        }
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        logger.error("Timeout occured");
        TestCase.fail("unexpected event");
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.info("IOException occured");
        TestCase.fail("unexpected exception io exception");
    }

    public SipProvider createSipProvider() {
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(host, port, transport);

            sipProvider = sipStack.createSipProvider(listeningPoint);
            return sipProvider;
        } catch (Exception ex) {
            logger.error(unexpectedException, ex);
            TestCase.fail(unexpectedException);
            return null;
        }

    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        logger.info("Transaction terminated event occured -- cleaning up");
        if (!transactionTerminatedEvent.isServerTransaction()) {
            ClientTransaction ct = transactionTerminatedEvent.getClientTransaction();
            for (Iterator<ClientTransaction> it = this.clientTxTable.values().iterator(); it.hasNext();) {
                if (it.next().equals(ct)) {
                    it.remove();
                }
            }
        } else {
            logger.info("Server tx terminated! ");
        }
    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        TestCase.fail("unexpected event");
    }

    private int[] targetPorts;
    public Proxy(int myPort, int ntargets, int[] targetPorts) {
        this.port = myPort;
        this.ntargets = ntargets;
        ProtocolObjects protocolObjects = new ProtocolObjects("proxy-"+myPort, "gov.nist", "udp", false, false, false);
        addressFactory = protocolObjects.addressFactory;
        headerFactory = protocolObjects.headerFactory;
        this.sipStack = protocolObjects.sipStack;
        this.targetPorts = targetPorts;
        this.sipProvider = this.createSipProvider();
        try {
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            logger.error(unexpectedException, ex);
            TestCase.fail(unexpectedException);

        }
    }

    public void stop() {
       this.sipStack.stop();
    }

}
