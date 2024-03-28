package performance.b2bua;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;

public class Test implements SipListener {
 
	private static final String SIP_BIND_ADDRESS = "javax.sip.IP_ADDRESS";
	private static final String SIP_PORT_BIND = "javax.sip.PORT";
	
	private SipFactory sipFactory;
	private SipStack sipStack;
	private SipProvider provider;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
		
	private static final String myAddress = "127.0.0.1";

    private static final int myPort = 5060;

	public Test() throws NumberFormatException, SipException, TooManyListenersException, InvalidArgumentException, ParseException {
		initStack();
	}
	
	private void initStack() throws SipException, TooManyListenersException,
			NumberFormatException, InvalidArgumentException, ParseException {
		this.sipFactory = SipFactory.getInstance();
		this.sipFactory.setPathName("gov.nist");
		Properties properties = new Properties();
        try {
            String filePath = System.getProperty("SIP_STACK_PROPERTIES_PATH");
            if (filePath == null) {
                //throw new RuntimeException("SIP_STACK_PROPERTIES_PATH environment variable not set");
            	filePath = "src/test/resources/performance/uas/sip-stack.properties";
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
		this.sipStack.start();
		ListeningPoint udpListeningPoint = sipStack.createListeningPoint(properties.getProperty(
			SIP_BIND_ADDRESS, myAddress), Integer.valueOf(properties
			.getProperty(SIP_PORT_BIND, "" + myPort)), ListeningPoint.UDP);
		ListeningPoint tcpListeningPoint = sipStack.createListeningPoint(properties.getProperty(
			SIP_BIND_ADDRESS, myAddress), Integer.valueOf(properties
			.getProperty(SIP_PORT_BIND, "" + myPort)), ListeningPoint.TCP);
		ListeningPoint tlsListeningPoint = sipStack.createListeningPoint(properties.getProperty(
			SIP_BIND_ADDRESS, myAddress), Integer.valueOf(properties
			.getProperty(SIP_PORT_BIND, "" + (myPort + 1))), ListeningPoint.TLS);
		provider = sipStack.createSipProvider( udpListeningPoint);
		provider.addSipListener(this);
		provider.addListeningPoint(tcpListeningPoint);
		provider.addListeningPoint(tlsListeningPoint);		
		this.headerFactory = sipFactory.createHeaderFactory();
		this.messageFactory = sipFactory.createMessageFactory();
	}

	private AtomicLong counter = new AtomicLong();
	
	private String getNextCounter() {
		long l = counter.incrementAndGet();
		return Long.toString(l);
	}
	
	// XXX -- SipListenerMethods - here we process incoming data

	public void processIOException(IOExceptionEvent arg0) {}

	public void processRequest(RequestEvent requestEvent) {

		if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
			TestCall call = new TestCall(getNextCounter(),provider,headerFactory,messageFactory);
			call.processInvite(requestEvent);
			
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.BYE)) {
			Dialog dialog = requestEvent.getDialog();
			if (dialog != null) {
				((TestCall)dialog.getApplicationData()).processBye(requestEvent);
			}
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.ACK)) {
			Dialog dialog = requestEvent.getDialog();
			if (dialog != null) {
				((TestCall)dialog.getApplicationData()).processAck(requestEvent);
			}
		}
		else {
			System.err.println("Received unexpected sip request: "+requestEvent.getRequest());
			Dialog dialog = requestEvent.getDialog();
			if (dialog != null) {
				dialog.setApplicationData(null);
			}
		}
	}

	public void processResponse(ResponseEvent responseEvent) {

		Dialog dialog = responseEvent.getDialog();
		if (dialog != null) {
			if (responseEvent.getClientTransaction() == null) {
				// retransmission, drop it
				return;
			}
			TestCall call = (TestCall) dialog.getApplicationData();
			if (call != null) {				
				switch (responseEvent.getResponse().getStatusCode()) {
				case 100:
					// ignore
					break;
				case 180:
					call.process180(responseEvent);
					break;
				case 200:
					call.process200(responseEvent);
					break;	
				default:
					System.err.println("Received unexpected sip response: "+responseEvent.getResponse());
					dialog.setApplicationData(null);
					break;
				}
			} else {
				System.err
						.println("Received response on dialog with id that does not matches a active call: "+responseEvent.getResponse());
			}
		} else {
			System.err.println("Received response without dialog: "+responseEvent.getResponse());
		}
	}

	public void processTimeout(TimeoutEvent arg0) {}

	public void processTransactionTerminated(
			TransactionTerminatedEvent txTerminatedEvent) {}

	public void processDialogTerminated(DialogTerminatedEvent dte) {
		dte.getDialog().setApplicationData(null);
	}
	
	public static void main(String[] args) {
		try {
			new Test();
			System.out.println("Test started");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
