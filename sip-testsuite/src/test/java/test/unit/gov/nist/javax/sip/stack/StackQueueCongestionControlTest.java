package test.unit.gov.nist.javax.sip.stack;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import gov.nist.javax.sip.DialogExt;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import gov.nist.javax.sip.stack.transports.processors.nio.NioMessageProcessorFactory;
import junit.framework.TestCase;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * Testing for deadlock under massive load on the same call.
 * This testcase reproduces deadlock that occurs most frequently with TCP thread pool size set to 1
 * from this issue https://jain-sip.dev.java.net/issues/show_bug.cgi?id=301
 * 
 * Other related issue is here http://code.google.com/p/mobicents/issues/detail?id=1810
 * 
 * The test sends couple of thousands 180 Ringing responses in order to stall the UAC thread.
 * 
 * The issue is more easily reproducible without debug logs - 300 vs 1000 messages on average
 * 
 * @author vralev
 *
 */
public class StackQueueCongestionControlTest extends TestCase {

	public class Shootme implements SipListener {

        private  AddressFactory addressFactory;

        private  MessageFactory messageFactory;

        private  HeaderFactory headerFactory;

        private SipStack sipStack;

        private SipProvider sipProvider;

        private static final String myAddress = "127.0.0.1";

        private final int myPort = NetworkPortAssigner.retrieveNextPort();



        private DialogExt dialog;

        public static final boolean callerSendsBye = true;




        public void processRequest(RequestEvent requestEvent) {
            Request request = requestEvent.getRequest();
            ServerTransaction serverTransactionId = requestEvent
                    .getServerTransaction();

            System.out.println("\n\nRequest " + request.getMethod()
                    + " received at " + sipStack.getStackName()
                    + " with server transaction id " + serverTransactionId);

            if (request.getMethod().equals(Request.INVITE)) {
                processInvite(requestEvent, serverTransactionId);
            } else if(request.getMethod().equals(Request.ACK)) {
                processAck(requestEvent, serverTransactionId);
            }

        }

        private int num = 0;

        public void processResponse(ResponseEvent responseEvent) {
            num++;
            if(num<5) {
                try {
                    System.out.println("shootme: got an OK response! ");
                    System.out.println("Dialog State = " + dialog.getState());
                    SipProvider provider = (SipProvider) responseEvent.getSource();

                    Request messageRequest = dialog.createRequest(Request.MESSAGE);
                    CSeqHeader cseq = (CSeqHeader)messageRequest.getHeader(CSeqHeader.NAME);

                    // We will test if the CSEq validation is off by sending CSeq 1 again
                    cseq.setSeqNumber(1);
                    ClientTransaction ct = provider
                    .getNewClientTransaction(messageRequest);
                    dialog.sendRequest(ct);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else if (num == 5){
                try {
                    System.out.println("shootme: got an OK response! ");
                    System.out.println("Dialog State = " + dialog.getState());
                    SipProvider provider = (SipProvider) responseEvent.getSource();

                    Request messageRequest = dialog.createRequest(Request.BYE);

                    ClientTransaction ct = provider
                    .getNewClientTransaction(messageRequest);
                    dialog.sendRequest(ct);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            if(responseEvent.getResponse().getStatusCode() == 500) {
                fail("We received some error. It should not happen with loose dialog validation. We should not receive error on cseq out of order");
            }
        }

        int acks = 0;
        /**
         * Process the ACK request. Send the bye and complete the call flow.
         */
        public void processAck(RequestEvent requestEvent,
                ServerTransaction serverTransaction) {
            acks++;
            // We will wait for 5 acks to test if retransmissions are filtered. With loose dialog
            // validation the ACK retransmissions are not filtered by the stack.
            if(acks == 5)
            {
                try {
                    System.out.println("shootme: got an ACK! ");
                    System.out.println("Dialog State = " + dialog.getState());
                    SipProvider provider = (SipProvider) requestEvent.getSource();

                    Request messageRequest = dialog.createRequest(Request.MESSAGE);
                    CSeqHeader cseq = (CSeqHeader)messageRequest.getHeader(CSeqHeader.NAME);

                    // We will test if the CSEq validation is off by sending CSeq 1 again

                    ClientTransaction ct = provider
                    .getNewClientTransaction(messageRequest);
                    cseq.setSeqNumber(1);
                    ct.sendRequest();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        }
        int volume;
        int sentResponses;

        /**
         * Process the invite request.
         */
        public void processInvite(RequestEvent requestEvent,
                ServerTransaction serverTransaction) {
        
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
  
            Request request = requestEvent.getRequest();
            try {
                serverTransaction = sipProvider.getNewServerTransaction(request);
                dialog = (DialogExt) sipProvider.getNewDialog(serverTransaction);
                dialog.disableSequenceNumberValidation();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            for(int q = 0; q<volume; q++) {
            	sentResponses ++;
            	try {
            		Response okResponse = messageFactory.createResponse(180,
            				request);
            		okResponse.addHeader(headerFactory.createHeader("Number", q+""));
            		FromHeader from = (FromHeader) okResponse.getHeader(FromHeader.NAME);
            		from.removeParameter("tag");
            		Address address = addressFactory.createAddress("Shootme <sip:"
            				+ myAddress + ":" + myPort + ">");
            		ContactHeader contactHeader = headerFactory
            		.createContactHeader(address);
            		ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
            		toHeader.setTag("4321"); // Application is supposed to set.

            		FromHeader fromHeader = (FromHeader)okResponse.getHeader(FromHeader.NAME);
            		fromHeader.setTag("12345");
            		okResponse.addHeader(contactHeader);
            		serverTransaction.sendResponse(okResponse);


            	} catch (Exception ex) {
            		ex.printStackTrace();
            		//junit.framework.TestCase.fail("Exit JVM");
            	}
            	if(q%10==0) System.out.println("Send " + q);
            }
            try {
            	System.out.println("SHOOTME Sends 200 OK");
                Response okResponse = messageFactory.createResponse(200,
                        request);
                FromHeader from = (FromHeader) okResponse.getHeader(FromHeader.NAME);
                from.removeParameter("tag");
                Address address = addressFactory.createAddress("Shootme <sip:"
                        + myAddress + ":" + myPort + ">");
                ContactHeader contactHeader = headerFactory
                        .createContactHeader(address);
                ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
                toHeader.setTag("4321"); // Application is supposed to set.

                FromHeader fromHeader = (FromHeader)okResponse.getHeader(FromHeader.NAME);
                fromHeader.setTag("12345");
                okResponse.addHeader(contactHeader);
                serverTransaction.sendResponse(okResponse);


            } catch (Exception ex) {
                ex.printStackTrace();
                //junit.framework.TestCase.fail("Exit JVM");
            }
        }






        public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
            Transaction transaction;
            if (timeoutEvent.isServerTransaction()) {
                transaction = timeoutEvent.getServerTransaction();
            } else {
                transaction = timeoutEvent.getClientTransaction();
            }
            System.out.println("state = " + transaction.getState());
            System.out.println("dialog = " + transaction.getDialog());
            System.out.println("dialogState = "
                    + transaction.getDialog().getState());
            System.out.println("Transaction Time out");
        }

        public void init(String transport, int volume) {
        	this.volume = volume;
            SipFactory sipFactory = null;
            sipStack = null;
            sipFactory = SipFactory.getInstance();
            sipFactory.resetFactory();
            sipFactory.setPathName("gov.nist");
            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "shootme");
            // You need 16 for logging traces. 32 for debug + traces.
            // Your code will limp at 32 but it is best for debugging.
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                    "shootmedebug.txt");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                    "shootmelog.txt");
            properties.setProperty("gov.nist.javax.sip.AUTOMATIC_DIALOG_ERROR_HANDLING", "false");
            properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
            if(System.getProperty("enableNIO") != null && System.getProperty("enableNIO").equalsIgnoreCase("true")) {
            	properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());
            }
            if(System.getProperty("enableNetty") != null && System.getProperty("enableNetty").equalsIgnoreCase("true")) {
                properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NettyMessageProcessorFactory.class.getName());
            }
            try {
                // Create SipStack object
                sipStack = sipFactory.createSipStack(properties);
                System.out.println("sipStack = " + sipStack);
            } catch (PeerUnavailableException e) {
                // could not find
                // gov.nist.jain.protocol.ip.sip.SipStackImpl
                // in the classpath
                e.printStackTrace();
                System.err.println(e.getMessage());
                if (e.getCause() != null)
                    e.getCause().printStackTrace();
                //junit.framework.TestCase.fail("Exit JVM");
            }

            try {
                headerFactory = sipFactory.createHeaderFactory();
                addressFactory = sipFactory.createAddressFactory();
                messageFactory = sipFactory.createMessageFactory();
                ListeningPoint lp = sipStack.createListeningPoint("127.0.0.1",
                        myPort, transport);

                Shootme listener = this;

                sipProvider = sipStack.createSipProvider(lp);
                System.out.println("udp provider " + sipProvider);
                sipProvider.addSipListener(listener);

            } catch (Exception ex) {
                ex.printStackTrace();
                fail("Unexpected exception");
            }

        }



        public void processIOException(IOExceptionEvent exceptionEvent) {
            fail("IOException " + exceptionEvent.getSource());

        }

        public void processTransactionTerminated(
                TransactionTerminatedEvent transactionTerminatedEvent) {
            if (transactionTerminatedEvent.isServerTransaction())
                System.out.println("Transaction terminated event recieved"
                        + transactionTerminatedEvent.getServerTransaction());
            else
                System.out.println("Transaction terminated "
                        + transactionTerminatedEvent.getClientTransaction());

        }

        public void processDialogTerminated(
                DialogTerminatedEvent dialogTerminatedEvent) {
            Dialog d = dialogTerminatedEvent.getDialog();
            System.out.println("Local Party = " + d.getLocalParty());

        }

        public void terminate() {
        	Utils.stopSipStack(this.sipStack);
            this.sipStack = null;
        }

    }

    public class Shootist implements SipListener {

        private  SipProvider sipProvider;

        private AddressFactory addressFactory;

        private MessageFactory messageFactory;

        private  HeaderFactory headerFactory;

        private SipStack sipStack;

        private ContactHeader contactHeader;

        private ListeningPoint udpListeningPoint;

        boolean messageSeen = false;

        public int receivedResponses=0;
        public AtomicBoolean inUse = new AtomicBoolean(false);
        public int sleep;

        private final int myPort = NetworkPortAssigner.retrieveNextPort();        

        private  String PEER_ADDRESS;

        private  int PEER_PORT;

        private  String peerHostPort;

        public Shootist(Shootme shootme) {
            PEER_ADDRESS = Shootme.myAddress;
            PEER_PORT = shootme.myPort;
            peerHostPort = PEER_ADDRESS + ":" + PEER_PORT;             
        }
        


        public void processRequest(RequestEvent requestReceivedEvent) {
            Request request = requestReceivedEvent.getRequest();
            if(request.getMethod().equalsIgnoreCase("message")) {
                messageSeen = true;
            }
            try {
            	System.out.println("SHOOTING Sends 200 OK");
                Response response = messageFactory.createResponse(200, request);
                requestReceivedEvent.getServerTransaction().sendResponse(response);
            } catch (Exception e) {
                e.printStackTrace();fail("Error");
            }


        }

        private int lastNumber = -1;
        public void processResponse(ResponseEvent responseReceivedEvent) {
        	try {        	
        		if(!inUse.compareAndSet(false, true)) {
        			fail("Concurrent responses should not happen");
        			throw new RuntimeException();
        		}
        		
        		Header h = responseReceivedEvent.getResponse().getHeader("Number");
        		if(h != null){
        			String n = h.toString().substring("Number:".length()).trim();
        			Integer i = Integer.parseInt(n);
        			if(i<=lastNumber) throw new RuntimeException("Messages out of order");
        			lastNumber = i;
        		}
        		
        		if(receivedResponses%100==0) System.out.println("Receive " + receivedResponses);
        		if ( responseReceivedEvent.getResponse().getStatusCode() == 180) {
        			receivedResponses++;
                    Thread.sleep(sleep);
        		}
        		if ( responseReceivedEvent.getResponse().getStatusCode() == Response.OK) {
        			Dialog d = responseReceivedEvent.getDialog();
                    System.out.println("dialog " + d + " tx " + responseReceivedEvent.getClientTransaction());
        			try {
        				Request ack = d.createAck(1);
        				sipProvider.sendRequest(ack);
        				sipProvider.sendRequest(ack);
        				sipProvider.sendRequest(ack);
        				sipProvider.sendRequest(ack);
        				sipProvider.sendRequest(ack);
        			} catch (Exception e) {
        				e.printStackTrace();
        				fail("Error sending ACK");
        			}
        		}
        	}catch(Exception e) {
        		e.printStackTrace();
        	} finally {
        		inUse.set(false);        		
        	}

        }
        

        public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
            System.out.println("Got a timeout " + timeoutEvent.getClientTransaction());
        }



        public void init(String threads, String timeout, int sleep, String transport) {
            SipFactory sipFactory = null;
            this.sleep = sleep;
            sipStack = null;
            sipFactory = SipFactory.getInstance();
            sipFactory.resetFactory();
            sipFactory.setPathName("gov.nist");
            Properties properties = new Properties();
            // If you want to try TCP transport change the following to
           // properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort + "/"
           //         + transport);
            // If you want to use UDP then uncomment this.
            properties.setProperty("javax.sip.STACK_NAME", "shootist");

            // The following properties are specific to nist-sip
            // and are not necessarily part of any other jain-sip
            // implementation.
            // You can set a max message size for tcp transport to
            // guard against denial of service attack.
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                    "shootistdebug.txt");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                    "shootistlog.txt");

            // Drop the client connection after we are done with the transaction.
            properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS",
                    "false");
            // Set to 0 (or NONE) in your production code for max speed.
            // You need 16 (or TRACE) for logging traces. 32 (or DEBUG) for debug + traces.
            // Your code will limp at 32 but it is best for debugging.
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "LOG4J");

            LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = logContext.getConfiguration();
            
            configuration.addAppender(ConsoleAppender.newBuilder().setName("Console").setLayout(PatternLayout.newBuilder().withPattern(PatternLayout.TTCC_CONVERSION_PATTERN).build()).build());
        	
            LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
    		loggerConfig.setLevel(Level.WARN);
    		logContext.updateLoggers();
    		    		
            if(threads!=null) {
            	
            	properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
            	properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", threads);
                properties.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", threads);
            }
            properties.setProperty("gov.nist.javax.sip.CONGESTION_CONTROL_TIMEOUT", timeout);
            properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
            properties.setProperty("gov.nist.javax.sip.AUTOMATIC_DIALOG_ERROR_HANDLING","false");
            if(System.getProperty("enableNIO") != null && System.getProperty("enableNIO").equalsIgnoreCase("true")) {
            	properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());
            }
            if(System.getProperty("enableNetty") != null && System.getProperty("enableNetty").equalsIgnoreCase("true")) {
                properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NettyMessageProcessorFactory.class.getName());
            }
            try {
                // Create SipStack object
                sipStack = sipFactory.createSipStack(properties);
                System.out.println("createSipStack " + sipStack);
            } catch (PeerUnavailableException e) {
                // could not find
                // gov.nist.jain.protocol.ip.sip.SipStackImpl
                // in the classpath
                e.printStackTrace();
                System.err.println(e.getMessage());
                fail("Problem with setup");
            }

            try {
                headerFactory = sipFactory.createHeaderFactory();
                addressFactory = sipFactory.createAddressFactory();
                messageFactory = sipFactory.createMessageFactory();
                udpListeningPoint = sipStack.createListeningPoint("127.0.0.1", myPort, transport);
                sipProvider = sipStack.createSipProvider(udpListeningPoint);
                Shootist listener = this;
                sipProvider.addSipListener(listener);

                String fromName = "BigGuy";
                String fromSipAddress = "here.com";
                String fromDisplayName = "The Master Blaster";

                String toSipAddress = "there.com";
                String toUser = "LittleGuy";
                String toDisplayName = "The Little Blister";

                // create >From Header
                SipURI fromAddress = addressFactory.createSipURI(fromName,
                        fromSipAddress);

                Address fromNameAddress = addressFactory.createAddress(fromAddress);
                fromNameAddress.setDisplayName(fromDisplayName);
                FromHeader fromHeader = headerFactory.createFromHeader(
                        fromNameAddress, "12345");

                // create To Header
                SipURI toAddress = addressFactory
                        .createSipURI(toUser, toSipAddress);
                Address toNameAddress = addressFactory.createAddress(toAddress);
                toNameAddress.setDisplayName(toDisplayName);
                ToHeader toHeader = headerFactory.createToHeader(toNameAddress,
                        null);

                // create Request URI
                SipURI requestURI = addressFactory.createSipURI(toUser,
                        peerHostPort);

                // Create ViaHeaders

                ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
                String ipAddress = udpListeningPoint.getIPAddress();
                ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress,
                        sipProvider.getListeningPoint(transport).getPort(),
                        transport, null);

                // add via headers
                viaHeaders.add(viaHeader);

                // Create ContentTypeHeader
                ContentTypeHeader contentTypeHeader = headerFactory
                        .createContentTypeHeader("application", "sdp");

                // Create a new CallId header
                CallIdHeader callIdHeader = sipProvider.getNewCallId();

                // Create a new Cseq header
                CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L,
                        Request.INVITE);

                // Create a new MaxForwardsHeader
                MaxForwardsHeader maxForwards = headerFactory
                        .createMaxForwardsHeader(70);

                // Create the request.
                Request request = messageFactory.createRequest(requestURI,
                        Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
                        toHeader, viaHeaders, maxForwards);
                // Create contact headers
                String host = "127.0.0.1";

                SipURI contactUrl = addressFactory.createSipURI(fromName, host);
                contactUrl.setPort(udpListeningPoint.getPort());
                contactUrl.setLrParam();

                // Create the contact name address.
                SipURI contactURI = addressFactory.createSipURI(fromName, host);
                contactURI.setPort(sipProvider.getListeningPoint(transport)
                        .getPort());

                Address contactAddress = addressFactory.createAddress(contactURI);

                // Add the contact address.
                contactAddress.setDisplayName(fromName);

                contactHeader = headerFactory.createContactHeader(contactAddress);
                request.addHeader(contactHeader);

                // You can add extension headers of your own making
                // to the outgoing SIP request.
                // Add the extension header.
                Header extensionHeader = headerFactory.createHeader("My-Header",
                        "my header value");
                request.addHeader(extensionHeader);

                String sdpData = "v=0\r\n"
                        + "o=4855 13760799956958020 13760799956958020"
                        + " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n"
                        + "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
                        + "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n"
                        + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
                        + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
                byte[] contents = sdpData.getBytes();

                request.setContent(contents, contentTypeHeader);
                // You can add as many extension headers as you
                // want.

                extensionHeader = headerFactory.createHeader("My-Other-Header",
                        "my new header value ");
                request.addHeader(extensionHeader);

                Header callInfoHeader = headerFactory.createHeader("Call-Info",
                        "<http://www.antd.nist.gov>");
                request.addHeader(callInfoHeader);

                // Create the client transaction.
                ClientTransaction inviteTid = sipProvider.getNewClientTransaction(request);
            	try {
					sipProvider.getNewDialog(inviteTid);
				} catch (SipException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

                // send the request out.
                inviteTid.sendRequest();
                inviteTid.getDialog();
            } catch (Exception ex) {
            	ex.printStackTrace();
                fail("cannot create or send initial invite");
            }
        }



        public void processIOException(IOExceptionEvent exceptionEvent) {
            System.out.println("IOException happened for "
                    + exceptionEvent.getHost() + " port = "
                    + exceptionEvent.getPort());

        }

        public void processTransactionTerminated(
                TransactionTerminatedEvent transactionTerminatedEvent) {
            System.out.println("Transaction terminated event recieved");
        }

        public void processDialogTerminated(
                DialogTerminatedEvent dialogTerminatedEvent) {
            System.out.println("dialogTerminatedEvent");

        }
        public void terminate() {
        	sipProvider = null;
        	Utils.stopSipStack(this.sipStack);
            this.sipStack = null;
        }
    }

    private test.unit.gov.nist.javax.sip.stack.StackQueueCongestionControlTest.Shootme shootme;
    private test.unit.gov.nist.javax.sip.stack.StackQueueCongestionControlTest.Shootist shootist;

    public void setUp() {
        this.shootme = new Shootme();
        this.shootist = new Shootist(shootme);


    }
    public void tearDown() {
        shootist.terminate();
        shootme.terminate();
    }

    private static final int TIMEOUT = 10000;

    public void testTCPZeroLostMessages() {
        this.shootme.init("tcp",200);
        this.shootist.init("10", "10000", 2, "tcp");
        try {
            Thread.sleep(TIMEOUT);
        } catch (Exception ex) {

        }
        if(this.shootist.receivedResponses<=1) {
           // fail("We excpeted more than 0" + this.shootist.receivedResponses);
        }
        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);
        System.out.println("ACKs retrans received  " + shootme.acks);
        
        assertEquals(shootme.sentResponses, shootist.receivedResponses);
        if(this.shootme.acks != 5) {
            fail("We expect 5 ACKs because retransmissions are not filtered in loose dialog validation. We got " + this.shootme.acks);
        }
    }
    

    
    /*public void testUDPHugeLoss() {
        this.shootme.init("udp",1000);
        this.shootist.init("10", "10", 20, "udp");
        try {
            Thread.sleep(10000);
        } catch (Exception ex) {

        }

        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);
        System.out.println("ACKs retrans received  " + shootme.acks);

        if(this.shootist.receivedResponses<=1) {
            fail("We excpeted more than 0" + this.shootist.receivedResponses);
        }
        assertTrue(shootist.receivedResponses<shootme.sentResponses/2);
       
    }    
    
    public void testUDPIdle() throws InterruptedException {
        this.shootme.init("udp",1);
        this.shootist.init("10", "10", 6000, "udp");
        
        AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return shootme.acks > 5;
            }
        } , TIMEOUT);
        
        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);
        System.out.println("ACKs retrans received  " + shootme.acks);

        if(this.shootme.acks < 5) {
            fail("We expect at least 5 ACKs because retransmissions are not filtered in loose dialog validation." + this.shootme.acks);
        }
    }
    
    public void testUDPNoThreadpool() throws InterruptedException {
        this.shootme.init("udp",100);
        this.shootist.init(null, "1", 1, "udp");
        
        AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return shootist.receivedResponses > 1 && shootme.acks == 5;
            }
        } , TIMEOUT);
        
        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);
        System.out.println("ACKs retrans received  " + shootme.acks);

        if(this.shootist.receivedResponses<=1) {
            fail("We expected more than 0" + this.shootist.receivedResponses);
        }
        if(this.shootme.acks != 5) {
            fail("We expect 5 ACKs because retransmissions are not filtered in loose dialog validation.");
        }
    }
    
    public void testTCPNoThreadpool() throws InterruptedException {
        this.shootme.init("tcp",100);
        this.shootist.init(null, "1", 1, "tcp");
        
        AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return shootist.receivedResponses > 1 && shootme.acks == 5;
            }
        } , TIMEOUT);
        
        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);

        if(this.shootist.receivedResponses<=1) {
            fail("We expected more than 0" + this.shootist.receivedResponses);
        }
        if(this.shootme.acks != 5) {
            fail("We expect 5 ACKs because retransmissions are not filtered in loose dialog validation.");
        }
    }
    
    public void testTCPCongestionControlOff() throws InterruptedException {
        this.shootme.init("tcp",100);
        this.shootist.init("10","0",1,"tcp");
        
        AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return shootist.receivedResponses > 1 && 
                        shootist.receivedResponses == shootme.sentResponses ;
            }
        } , TIMEOUT);
        
        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);        

        if(this.shootist.receivedResponses<=1) {
            fail("We expected more than 0" + this.shootist.receivedResponses);
        }
        assertEquals(shootme.sentResponses, shootist.receivedResponses);

    }
    public void testTCPHugeLoss() throws InterruptedException {
        this.shootme.init("tcp",100);
        this.shootist.init("10", "10", 20, "tcp");
        AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return shootist.receivedResponses > 1 && 
                        shootist.receivedResponses > shootme.sentResponses/2 ;
            }
        } , TIMEOUT);
        
        if(this.shootist.receivedResponses<=1) {
            fail("We expected more than 0" + this.shootist.receivedResponses);
        }
        System.out.println("received responses " + shootist.receivedResponses);
        System.out.println("sent Responses " + shootme.sentResponses);
        assertTrue("received responses " + shootist.receivedResponses + " sent Responses " + shootme.sentResponses, 
            shootist.receivedResponses > shootme.sentResponses/2);
        assertTrue("received responses " + shootist.receivedResponses + " sent Responses " + shootme.sentResponses, 
            shootist.receivedResponses < shootme.sentResponses);
       
    }*/
    public void plusTest() {
    	long a = 1;
    	while(a>0) a+=a;
    	System.out.println(a);
    }

}
