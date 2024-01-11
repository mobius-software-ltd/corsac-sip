package performance.uas;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.javax.sip.message.RequestExt;


/**
 * This is the UAS application for performance testing
 *
 * @author Vladimir Ralev
 */
public class Shootme implements SipListener {
    private static final String SIP_BIND_ADDRESS = "javax.sip.IP_ADDRESS";
	private static final String SIP_PORT_BIND = "javax.sip.PORT";

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private static SipStack sipStack;
    
    private static SipFactory sipFactory;
    
    private static SipProvider sipProvider; 
    
    private static Timer timer = new Timer();

    private static final String myAddress = "127.0.0.1";

    private static final int myPort = 5080;
        
    protected static final String usageString = "java "
            + Shootme.class.getCanonicalName() + " \n"
            + ">>>> is your class path set to the root?";

	private static final long BYE_DELAY = 5000;

	private static final String TIMER_USER = "sipp-timer";

    private static void usage() {
        System.out.println(usageString);
        System.exit(2);
    }

    class ByeTask extends TimerTask {
        Dialog dialog;
        
        public ByeTask(Dialog dialog)  {
            this.dialog = dialog;
        }
        public void run () {
            try {
               Request byeRequest = this.dialog.createRequest(Request.BYE);
               ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
               dialog.sendRequest(ct);
               dialog = null;
            } catch (Exception ex) {
                ex.printStackTrace();                
            }

        }
    }
    
    public void processRequest(RequestEvent requestEvent) {
        final Request request = requestEvent.getRequest();
        final ServerTransaction serverTransactionId = requestEvent.getServerTransaction();

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.CANCEL)) {
            processCancel(requestEvent, serverTransactionId);
        }

    }

    public void processResponse(ResponseEvent responseEvent) {
    }

    /**
     * Process the ACK request.
     */
    public void processAck(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
    	final Dialog dialog = requestEvent.getDialog();
    	final RequestExt request = (RequestExt) requestEvent.getRequest();
    	if(((SipURI)request.getFromHeader().getAddress().getURI()).getUser().equalsIgnoreCase(TIMER_USER)) {
    		timer.schedule(new ByeTask(dialog), BYE_DELAY) ;
    	}
    }

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {

        final Request request = requestEvent.getRequest();
        final SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        ServerTransaction st = serverTransaction;        
        try {
        	if (st == null) {
        		st = sipProvider.getNewServerTransaction(request);
            }
        	sipProvider.getNewDialog(st);
        	final String toTag = ""+System.nanoTime();
            Response response = messageFactory.createResponse(Response.RINGING,
                    request);            
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag(toTag); // Application is supposed to set.            
			// Creates a dialog only for non trying responses				
            st.sendResponse(response);

            response = messageFactory.createResponse(Response.OK,
                    request);
            final Address address = addressFactory.createAddress("Shootme <sip:"
                    + myAddress + ":" + myPort + ">");
            final ContactHeader contactHeader = headerFactory
                    .createContactHeader(address);
            response.addHeader(contactHeader);
            toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag(toTag); // Application is supposed to set.
            st.sendResponse(response);
        } catch (Exception ex) {
            ex.printStackTrace();
            //junit.framework.TestCase.fail("Exit JVM");
        }
    }


    /**
     * Process the bye request.
     */
    public void processBye(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        final Request request = requestEvent.getRequest();
        // final Dialog dialog = requestEvent.getDialog();
        try {
            final Response response = messageFactory.createResponse(200, request);
            if(serverTransactionId == null) {
            	serverTransactionId = ((SipProvider)requestEvent.getSource()).getNewServerTransaction(request);
            }
            serverTransactionId.sendResponse(response);
        } catch (Exception ex) {
            ex.printStackTrace();
            //junit.framework.TestCase.fail("Exit JVM");

        }
    }

    public void processCancel(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
    	// Request request = null;
    	if(timeoutEvent.getClientTransaction() == null) {
    		// request = 
                timeoutEvent.getServerTransaction().getRequest();
    	} else {
    		// request = 
                timeoutEvent.getClientTransaction().getRequest();
    	}
    	//System.out.println(request);
    }

    public void init() {                
        sipStack = null;
        sipFactory = SipFactory.getInstance();

        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        try {
            String filePath = System.getProperty("SIP_STACK_PROPERTIES_PATH");
            if (filePath == null) {
                throw new RuntimeException("SIP_STACK_PROPERTIES_PATH environment variable not set");
            }
            properties.load(new FileInputStream(new File(filePath)));
            if(System.getProperty("javax.sip.IP_ADDRESS") != null) {
            	properties.setProperty("javax.sip.IP_ADDRESS", System.getProperty("javax.sip.IP_ADDRESS"));
            }
            if(System.getProperty("javax.sip.TRANSPORT") != null) {
            	properties.setProperty("javax.sip.TRANSPORT", System.getProperty("javax.sip.TRANSPORT"));
            }
            if(System.getProperty("javax.sip.PORT") != null) {
            	properties.setProperty("javax.sip.PORT", System.getProperty("javax.sip.PORT"));
            }
            // Create SipStack object
            sipStack = sipFactory.createSipStack(properties);
        } catch (Exception e) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            e.printStackTrace();
            System.err.println(e.getMessage());
            if (e.getCause() != null)
                e.getCause().printStackTrace();
            System.exit(2);
        }

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            
            Shootme listener = this;

            ListeningPoint udpListeningPoint = sipStack.createListeningPoint(properties.getProperty(
                SIP_BIND_ADDRESS, myAddress), Integer.valueOf(properties
                .getProperty(SIP_PORT_BIND, "" + myPort)), ListeningPoint.UDP);
            ListeningPoint tcpListeningPoint = sipStack.createListeningPoint(properties.getProperty(
                SIP_BIND_ADDRESS, myAddress), Integer.valueOf(properties
                .getProperty(SIP_PORT_BIND, "" + myPort)), ListeningPoint.TCP);
            ListeningPoint tlsListeningPoint = sipStack.createListeningPoint(properties.getProperty(
                SIP_BIND_ADDRESS, myAddress), Integer.valueOf(properties
                .getProperty(SIP_PORT_BIND, "" + (myPort + 1))), ListeningPoint.TLS);
            sipProvider = sipStack.createSipProvider( udpListeningPoint);
            sipProvider.addSipListener(listener);
            sipProvider.addListeningPoint(tcpListeningPoint);
            sipProvider.addListeningPoint(tlsListeningPoint);		

        } catch (Exception ex) {
            ex.printStackTrace();
            usage();
        }

    }

    public static void main(String args[]) {
        new Shootme().init();
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
    	// Request request = null;
    	if(transactionTerminatedEvent.getClientTransaction() == null) {
    		// request = 
                transactionTerminatedEvent.getServerTransaction().getRequest();
    	} else {
    		// request = 
                transactionTerminatedEvent.getClientTransaction().getRequest();
    	}
    	//System.out.println(request);
    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
    	//Dialog dialog = dialogTerminatedEvent.getDialog();
    	//System.out.println(dialog);
    }

}
