package test.unit.gov.nist.javax.sip.stack.dialog.b2bua;

import java.util.Hashtable;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.Transaction;
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RequireHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.message.ResponseExt;
import junit.framework.TestCase;
import test.tck.TestHarness;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;



/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 *
 * @author M. Ranganathan
 */

public class Shootme   implements SipListener {




    private static final String myAddress = "127.0.0.1";

    private Hashtable<String,ServerTransaction> serverTxTable = new Hashtable<String,ServerTransaction>();

    private SipProvider sipProvider;

    private int myPort ;

    private static String unexpectedException = "Unexpected exception ";

    private static Logger logger = LogManager.getLogger(Shootme.class);

    private boolean inviteSeen;


    private boolean byeSeen;

    private boolean ackSeen;


    private SipStack sipStack;

    public int ringingDelay;
    public int okDelay;

    public boolean sendRinging;

    public boolean waitForCancel;
    public boolean receiveUpdate;

    protected boolean cancelSeen;

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private static final String transport = "udp";

    private static Timer timer = new Timer();

    private String toTag;

    public boolean sendReliableProvisionalResponse = false;

    private int numberOfPrackReceived = 0;

    private Request inviteRequest;

    private ServerTransaction inviteStx;

    private boolean updateSeen;    

    class MyTimerTask extends TimerTask {
        Request request;
        ServerTransaction serverTx;

        public MyTimerTask(Request request,  ServerTransaction tx) {
            logger.info("MyTimerTask ");
            this.request = request;
            this.serverTx = tx;

        }

        public void run() {
            if(!sendReliableProvisionalResponse) {
                sendInviteOK(request,serverTx);
            } else {
                if(numberOfPrackReceived >= 2) {
                    sendInviteOK(request,serverTx);
                } else {
                    sendRinging(request,serverTx);
                }
            }
        }

    }



    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent
                .getServerTransaction();

        logger.info("\n\nRequest " + request.getMethod()
                + " received at " + sipStack.getStackName()
                + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.UPDATE)) {
            processUpdate(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.CANCEL)) {
            processCancel(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.PRACK)) {
            processPrack(requestEvent, serverTransactionId);
        }

    }

    public void processResponse(ResponseEvent responseEvent) {        
        Response response = (Response) responseEvent.getResponse();
        logger.info("Got a response " + response);
        ClientTransaction tid = responseEvent.getClientTransaction();
        logger.info("Response received with client transaction id " + tid
                + ":\n" + response.getStatusCode());
        if (tid == null) {
            logger.info("Stray response -- dropping ");
            return;
        }
        logger.info("transaction state is " + tid.getState());
        logger.info("Dialog = " + tid.getDialog());
        logger.info("Dialog State is " + tid.getDialog().getState());
        try {
            logger.info("response = " + response);
            if (response.getStatusCode() == Response.OK && ((ResponseExt)response).getCSeqHeader().getMethod().equals(Request.UPDATE)) {
                timer.schedule(new MyTimerTask(inviteRequest,inviteStx), this.okDelay);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");
        }
    }

    private void processPrack(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        numberOfPrackReceived++;
        Dialog dialog =  serverTransaction.getDialog();

        try {
            logger.info("shootme: got an PRACK! ");
            logger.info("Dialog State = " + dialog.getState());

            /**
             * JvB: First, send 200 OK for PRACK
             */
            Request prack = requestEvent.getRequest();
            Response prackOk = messageFactory.createResponse(200, prack);
            serverTransaction.sendResponse(prackOk);

            if(numberOfPrackReceived >= 2) {
                timer.schedule(new MyTimerTask(inviteRequest,inviteStx), this.okDelay);
            } else {
                timer.schedule(new MyTimerTask(inviteRequest,inviteStx), this.ringingDelay);
            }           
        } catch (Exception ex) {
            TestHarness.fail(ex.getMessage());
        }
    }

    /**
     * Process the ACK request. Send the bye and complete the call flow.
     */
    public void processAck(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        logger.info("shootme: got an ACK! ");
        logger.info("Dialog = " + requestEvent.getDialog());
        if( requestEvent.getDialog() != null ) {
            logger.info("Dialog State = " + requestEvent.getDialog().getState());    
        }
        
        this.ackSeen = true;
        TestCase.assertEquals( DialogState.CONFIRMED , requestEvent.getDialog().getState() );
    }

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        inviteRequest = request;
        try {
            logger.info("shootme: got an Invite sending Trying");
            // logger.info("shootme: " + request);

            ServerTransaction st = requestEvent.getServerTransaction();

            if (st == null) {
                logger.info("null server tx -- getting a new one");
                st = sipProvider.getNewServerTransaction(request);
            }
            inviteStx = st;
            
            logger.info("getNewServerTransaction : " + st);

            String txId = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getBranch();
            this.serverTxTable.put(txId, st);

            // Create the 100 Trying response.
            Response response = messageFactory.createResponse(Response.TRYING,
                    request);            

            // Add a random sleep to stagger the two OK's for the benefit of implementations
            // that may not be too good about handling re-entrancy.
            int timeToSleep = (int) (Math.random() * 1000);
            System.out.println("UAC Time to sleep " + timeToSleep);
            Thread.sleep(timeToSleep);

            st.sendResponse(response);

            sendRinging(request, st);                        
            if(!waitForCancel && !sendReliableProvisionalResponse) {
                timer.schedule(new MyTimerTask(request, st), this.okDelay);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");
        }
    }

    private void sendRinging(Request request, ServerTransaction st) {
        
        ListeningPoint lp = sipProvider.getListeningPoint(transport);
        int myPort = lp.getPort();            
        try {
            Address address = addressFactory.createAddress("Shootme <sip:"
                + myAddress + ":" + myPort + ">");
            Response ringingResponse = messageFactory.createResponse(Response.RINGING,
                    request);
            ContactHeader contactHeader = headerFactory.createContactHeader(address);
            // ringingResponse.addHeader(contactHeader);
            ToHeader toHeader = (ToHeader) ringingResponse.getHeader(ToHeader.NAME);
            if(toTag == null) {
                toTag =  Integer.valueOf(new Random().nextInt()).toString();
            }

            toHeader.setTag(toTag);
            Dialog dialog =  st.getDialog();
            dialog.setApplicationData(st);
            this.inviteSeen = true;

            if(sendReliableProvisionalResponse) {                
                ringingResponse.setStatusCode(183);
                RequireHeader requireHeader = headerFactory
                        .createRequireHeader("100rel");
                request.addHeader(requireHeader);
                ringingResponse.addHeader(contactHeader);
                dialog.sendReliableProvisionalResponse(ringingResponse);
            } else if (sendRinging) {
                ringingResponse.addHeader(contactHeader);
                Thread.sleep(ringingDelay);
                st.sendResponse(ringingResponse);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");
        }
    }

    private void sendInviteOK(Request request, ServerTransaction inviteTid) {
        try {
            logger.info("sendInviteOK: " + inviteTid);
            if (inviteTid.getState() != TransactionState.COMPLETED) {
                logger.info("shootme: Dialog state before OK: "
                        + inviteTid.getDialog().getState());
                
                Response okResponse = messageFactory.createResponse(Response.OK,
                        request);
                    ListeningPoint lp = sipProvider.getListeningPoint(transport);
                int myPort = lp.getPort();


                ((ToHeader)okResponse.getHeader(ToHeader.NAME)).setTag(toTag);


                Address address = addressFactory.createAddress("Shootme <sip:"
                        + myAddress + ":" + myPort + ">");
                ContactHeader contactHeader = headerFactory
                        .createContactHeader(address);
                okResponse.addHeader(contactHeader);
                inviteTid.sendResponse(okResponse);
                logger.info("shootme: Dialog state after OK: "
                        + inviteTid.getDialog().getState());                
            } else {
                logger.info("sedInviteOK: inviteTid = " + inviteTid + " state = " + inviteTid.getState());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Process the bye request.
     */
    public void processBye(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        Request request = requestEvent.getRequest();
        try {
            logger.info("shootme:  got a bye sending OK.");
            logger.info("shootme:  dialog = " + requestEvent.getDialog());
            logger.info("shootme:  dialogState = " + requestEvent.getDialog().getState());
            Response response = messageFactory.createResponse(200, request);
            if ( serverTransactionId != null) {
                serverTransactionId.sendResponse(response);
            }
            logger.info("shootme:  dialogState = " + requestEvent.getDialog().getState());

            this.byeSeen = true;


        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");

        }
    }

    /**
     * Process the update request.
     */
    public void processUpdate(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        Request request = requestEvent.getRequest();
        try {
            logger.info("shootme:  got a update sending OK.");
            logger.info("shootme:  dialog = " + requestEvent.getDialog());
            logger.info("shootme:  dialogState = " + requestEvent.getDialog().getState());
            Response response = messageFactory.createResponse(200, request);
            if ( serverTransactionId != null) {
                serverTransactionId.sendResponse(response);
            }
            logger.info("shootme:  dialogState = " + requestEvent.getDialog().getState());

            this.updateSeen = true;


        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");

        }
    }

    public void processCancel(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        Request request = requestEvent.getRequest();
        SipProvider sipProvider = (SipProvider)requestEvent.getSource();
        try {
            cancelSeen = true;
            logger.info("shootme:  got a cancel. " );
            // Because this is not an In-dialog request, you will get a null server Tx id here.
            if (serverTransactionId == null) {
                serverTransactionId = sipProvider.getNewServerTransaction(request);
            }
            Response response = messageFactory.createResponse(200, request);
            ((ToHeader)response.getHeader(ToHeader.NAME)).setTag(toTag);
            serverTransactionId.sendResponse(response);

            String serverTxId = ((ViaHeader)response.getHeader(ViaHeader.NAME)).getBranch();
            ServerTransaction serverTx = (ServerTransaction) this.serverTxTable.get(serverTxId);
            if ( serverTx != null && (serverTx.getState().equals(TransactionState.TRYING) ||
                    serverTx.getState().equals(TransactionState.PROCEEDING))) {
                Request originalRequest = serverTx.getRequest();
                Response resp = messageFactory.createResponse(Response.REQUEST_TERMINATED,originalRequest);
                ((ToHeader)resp.getHeader(ToHeader.NAME)).setTag(toTag);
                serverTx.sendResponse(resp);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");

        }
    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        Transaction transaction;
        if (timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
        } else {
            transaction = timeoutEvent.getClientTransaction();
        }
        logger.info("state = " + transaction.getState());
        logger.info("dialog = " + transaction.getDialog());
        logger.info("dialogState = "
                + transaction.getDialog().getState());
        logger.info("Transaction Time out");
    }

    public SipProvider createProvider() {
        try {

            ListeningPoint lp = sipStack.createListeningPoint(myAddress,
                    myPort, transport);

            sipProvider = sipStack.createSipProvider(lp);
            logger.info("provider " + sipProvider);
            logger.info("sipStack = " + sipStack);
            return sipProvider;
        } catch (Exception ex) {
            logger.error(ex);
            TestCase.fail(unexpectedException);
            return null;

        }

    }

    public Shootme( int myPort, boolean sendRinging, int ringingDelay, int okDelay ) throws TooManyListenersException {
        this.myPort = myPort;
        this.ringingDelay = ringingDelay;
        this.okDelay = okDelay;
        this.sendRinging = sendRinging;

        ProtocolObjects sipObjects = new ProtocolObjects("shootme-"+myPort,"gov.nist","udp",true,false, false);
        addressFactory = sipObjects.addressFactory;
        messageFactory = sipObjects.messageFactory;
        headerFactory = sipObjects.headerFactory;
        this.sipStack = sipObjects.sipStack;
        this.createProvider();
        this.sipProvider.addSipListener(this);
    }



    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.info("IOException");

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        logger.info("Transaction terminated event recieved");

    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        logger.info("Dialog terminated event recieved");

    }
    
    public TestAssertion getAssertion() {
        return new TestAssertion() {            

            @Override
            public boolean assertCondition() {
                if(!waitForCancel) {
                    return inviteSeen && byeSeen && ackSeen;
                } else if(sendReliableProvisionalResponse) {
                    if(receiveUpdate) {
                        return inviteSeen && numberOfPrackReceived == 2 && byeSeen && ackSeen && updateSeen;
                    } else {
                        return inviteSeen && numberOfPrackReceived == 2 && byeSeen && ackSeen;
                    }                    
                } else {
                    return inviteSeen && cancelSeen;
                }
            }
        };
    }

    public void checkState() {
        TestCase.assertTrue("Should see invite", inviteSeen);

        TestCase.assertTrue("Should see BYE",byeSeen );

    }

    public boolean checkBye() {
        return this.byeSeen;
    }

    public void stop() {
        this.sipStack.stop();
    }


    /**
     * @return the ackSeen
     *
     * Exactly one Dialog must get an ACK.
     */
    public boolean isAckSeen() {
        return ackSeen;
    }



}
