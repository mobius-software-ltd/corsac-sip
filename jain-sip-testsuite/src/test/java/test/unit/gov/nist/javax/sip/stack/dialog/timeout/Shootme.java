/*
 * This source code has been contributed to the public domain by Mobicents
 *
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 *
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement.
 */
package test.unit.gov.nist.javax.sip.stack.dialog.timeout;

import java.util.Timer;
import java.util.TimerTask;

import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;

import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.DialogTimeoutEvent.Reason;
import gov.nist.javax.sip.SipListenerExt;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * This class receives an INVITE and sends a 180 and a 200 OK, the Shootist will not send the ACK to test is the Dialog Timeout Event is correctly passed to the application.
 * The timeout Reason should be ACK not received 
 *
 * @author jean deruelle
 */

public class Shootme implements SipListenerExt {

    class TTask extends TimerTask {

        RequestEvent requestEvent;

        ServerTransaction st;

        public TTask(RequestEvent requestEvent, ServerTransaction st) {
            this.requestEvent = requestEvent;
            this.st = st;
        }

        public void run() {
            Request request = requestEvent.getRequest();
            try {
                // logger.info("shootme: got an Invite sending OK");
                Response response = messageFactory.createResponse(180, request);
                ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
                Address address = addressFactory.createAddress("Shootme <sip:" + myAddress + ":" + myPort + ">");
                ContactHeader contactHeader = headerFactory.createContactHeader(address);
                response.addHeader(contactHeader);
                
                if(!protocolObjects.autoDialog) {
                	((SipProvider)requestEvent.getSource()).getNewDialog(st);
                }
                st.getDialog().setApplicationData("some junk");
                
                // logger.info("got a server tranasaction " + st);
                st.sendResponse(response); // send 180(RING)
                response = messageFactory.createResponse(200, request);
                toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
                String toTag = Integer.valueOf((int) (Math.random() * 100000)).toString()+"_ResponseCode_"+responseCodeToINFO;
                toHeader.setTag(toTag); // Application is supposed to set.                                
                
                response.addHeader(contactHeader);

                st.sendResponse(response);// send 200(OK)

            } catch (Exception ex) {
                ex.printStackTrace();
                DialogTimeoutTest.fail("Shootme: Failed in timer task!!!", ex);
            }

        }

    }


    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private boolean stateIsOk = false;
    
    private boolean receiveBye = false;

    private ProtocolObjects protocolObjects;

    private int responseCodeToINFO = 500;

    // To run on two machines change these to suit.
    public static final String myAddress = "127.0.0.1";

    public final int myPort = NetworkPortAssigner.retrieveNextPort();

    private static Logger logger = LogManager.getLogger(Shootme.class); 

    static {
    	LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
    	Configuration configuration = logContext.getConfiguration();
    	if (configuration.getAppenders().isEmpty()) {
        	configuration.addAppender(ConsoleAppender.newBuilder().setName("Console").build());
        }
    }

    public Shootme(ProtocolObjects protocolObjects) {
        this.protocolObjects = protocolObjects;
        stateIsOk = protocolObjects.autoDialog;	
    }

    public boolean checkState() {
        logger.info("shootme: checkState " + stateIsOk);
        return stateIsOk;
    }

    public SipProvider createSipProvider() throws Exception {
        ListeningPoint lp = protocolObjects.sipStack.createListeningPoint(myAddress, myPort, protocolObjects.transport);

        SipProvider sipProvider = protocolObjects.sipStack.createSipProvider(lp);
        return sipProvider;
    }

    public void init() {

        headerFactory = protocolObjects.headerFactory;
        addressFactory = protocolObjects.addressFactory;
        messageFactory = protocolObjects.messageFactory;

    }

    /**
     * Process the ACK request. Send the bye and complete the call flow.
     */
    public void processAck(RequestEvent requestEvent, ServerTransaction serverTransaction) {
        try {
            // logger.info("*** shootme: got an ACK "
            // + requestEvent.getRequest());
            if (serverTransaction == null) {
                logger.info("null server transaction -- ignoring the ACK!");
                return;
            }
            Dialog dialog = serverTransaction.getDialog();

            logger.info("Dialog Created = " + dialog.getDialogId() + " Dialog State = " + dialog.getState());

            logger.info("Waiting for INFO");

        } catch (Exception ex) {
            ex.printStackTrace();
            DialogTimeoutTest.fail("Shootme: Failed on process ACK", ex);
        }
    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {        
        logger.info("process DialogTerminatedEvent " + dialogTerminatedEvent + ", dialog:" + dialogTerminatedEvent.getDialog());
        logger.info("autodialog" + protocolObjects.autoDialog + ", receiveBye:" + receiveBye);
    	if(!protocolObjects.autoDialog && !receiveBye) {
    		stateIsOk = false;
    		DialogTimeoutTest.fail("This shouldn't be called since a dialogtimeout event should be passed to the application instead!");
    	} else {
    		stateIsOk = true;
    	}
    	TimerTask timerTask = new CheckAppData(dialogTerminatedEvent.getDialog());
        new Timer().schedule(timerTask, 15000);
    }

    public void processInfo(RequestEvent requestEvent) {
        try {
            Response info500Response = messageFactory.createResponse(this.responseCodeToINFO, requestEvent.getRequest());
            requestEvent.getServerTransaction().sendResponse(info500Response);
        } catch (Exception e) {

            e.printStackTrace();
            DialogTimeoutTest.fail("Shootme: Failed on process INFO", e);
        }

    }

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent, ServerTransaction serverTransaction) {
        try {
            // logger.info("ProcessInvite");
            Request request = requestEvent.getRequest();
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            // Note you need to create the Server Transaction
            // before the listener returns but you can delay sending the
            // response

            ServerTransaction st = sipProvider.getNewServerTransaction(request);

            TTask ttask = new TTask(requestEvent, st);
            int ttime = 100;

            new Timer().schedule(ttask, ttime);
        } catch (Exception ex) {
            ex.printStackTrace();
            DialogTimeoutTest.fail("Shootme: Failed on process INVITE", ex);
        }
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.info("IOException event");
        DialogTimeoutTest.fail("Got IOException event");
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction();

        logger.info("GOT REQUEST: " + request.getMethod());

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.INFO)) {
            processInfo(requestEvent);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent, serverTransactionId);
        } 

    }
    
    /**
     * Process the bye request.
     */
    public void processBye(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        Request request = requestEvent.getRequest();
        Dialog dialog = requestEvent.getDialog();
        logger.info("local party = " + dialog.getLocalParty());
        try {
            logger.info("shootme:  got a bye sending OK.");
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            logger.info("Dialog State is "
                    + serverTransactionId.getDialog().getState());

        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");

        }
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {
        // logger.info("Got a response");
        Response response = (Response) responseReceivedEvent.getResponse();
        Transaction tid = responseReceivedEvent.getClientTransaction();

        // logger.info("Response received with client transaction id "
        // + tid + ":\n" + response);

        logger.info("GOT RESPONSE: " + response.getStatusCode());
        try {
            if (response.getStatusCode() == Response.OK && ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod().equals(Request.INVITE)) {

                Dialog dialog = tid.getDialog();
                
                Request request = tid.getRequest();
                dialog.sendAck(request);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            DialogTimeoutTest.fail("Shootme: Failed on process response: " + response.getStatusCode(), ex);
        }

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
    	logger.info("shootme: process TimeoutEvent " + timeoutEvent);
        logger.info("shootme: autodialog" + protocolObjects.autoDialog);
    	
    	if(protocolObjects.autoDialog) {
    		DialogTimeoutTest.fail(
            	"Shootist: Exception on timeout, event shouldn't be thrown on automatic dailog creation by the stack");
    		stateIsOk = false;
    	}    	    	
        /*
         * logger.info("state = " + transaction.getState());
         * logger.info("dialog = " + transaction.getDialog());
         * logger.info("dialogState = " +
         * transaction.getDialog().getState());
         * logger.info("Transaction Time out" +
         * transaction.getBranchId());
         */

    }
    
    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        logger.info("shootme: processDialogTimeout " + timeoutEvent.getDialog());        

        DialogTimeoutEvent dialogAckTimeoutEvent = (DialogTimeoutEvent)timeoutEvent;
        Dialog timeoutDialog = dialogAckTimeoutEvent.getDialog();

        logger.info("shootme: dialog timeout " + timeoutEvent + ", reason: " + timeoutEvent.getReason() + " , dialog:" + timeoutDialog);
        if(timeoutDialog == null){
            DialogTimeoutTest.fail(
                    "Shootist: Exception on timeout, dialog shouldn't be null");
            stateIsOk = false;
            return;
        }        
        if(dialogAckTimeoutEvent.getReason() == Reason.AckNotReceived) {
        	stateIsOk = true;
        }
        TimerTask timerTask = new CheckAppData(timeoutDialog);
        new Timer().schedule(timerTask, 9000);
	}

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        // logger.info("TransactionTerminatedEvent");
    }

    public void setResponseCodeToINFO(int responseCodeToINFO) {
        this.responseCodeToINFO = responseCodeToINFO;

    }

	/**
	 * @param receiveBye the receiveBye to set
	 */
	public void setReceiveBye(boolean receiveBye) {
		this.receiveBye = receiveBye;
	}

	/**
	 * @return the receiveBye
	 */
	public boolean isReceiveBye() {
		return receiveBye;
	}

	class CheckAppData extends TimerTask {
	    Dialog dialog;
	    
	    public CheckAppData(Dialog dialog) {
            this.dialog = dialog;
        }
	    
        public void run() {             
            logger.info("Checking app data " + dialog.getApplicationData());
            if(dialog.getApplicationData() == null || !dialog.getApplicationData().equals("some junk")) {
                logger.info("process checkappdata : setting stateIsOK to false");        
            	stateIsOk = false;
                DialogTimeoutTest.fail("application data should never be null except if nullified by the application !");
            }            
        }
	}
}
