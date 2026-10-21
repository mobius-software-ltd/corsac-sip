package test.unit.gov.nist.javax.sip.stack.messagesize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.message.MessageExt;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Always on the Netty transport (whatever enableNetty says), that's what is tested.
 * Answers every MESSAGE with 200 and records its Call-ID.
 */
public class Shootme implements SipListenerExt {

    private static final StackLogger logger = CommonLogger.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final String transport;

    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final List<String> receivedMessages = Collections.synchronizedList(new ArrayList<String>());

    private volatile boolean ioExceptionSeen;

    /** stackProperties: extra stack properties, null for none */
    public Shootme(int port, String transport, Properties stackProperties) {
        this.port = port;
        this.transport = transport;
        Properties properties = new Properties();
        if (stackProperties != null) {
            properties.putAll(stackProperties);
        }
        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
                NettyMessageProcessorFactory.class.getName());
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, true, false,
                false, properties);
        this.messageFactory = protocolObjects.messageFactory;
        this.sipStack = protocolObjects.sipStack;
        try {
            ListeningPoint listeningPoint = sipStack.createListeningPoint(myAddress, port, transport);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootme", ex);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        logger.logInfo("shootme:" + port + " received " + method);
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            }
            if (method.equals(Request.MESSAGE)) {
                receivedMessages.add(((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId());
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } else {
                st.sendResponse(messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, request));
            }
        } catch (Exception ex) {
            logger.logError("shootme: unexpected exception processing " + method, ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        logger.logInfo("shootme:" + port + " unexpected response " + responseEvent.getResponse().getStatusCode());
    }

    public void processMessageSent(MessageExt messageSentEvent, TransactionExt transaction) {
    }

    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        logger.logInfo("dialog timeout event:" + timeoutEvent.getReason());
    }

    public void processTimeout(TimeoutEvent e) {
        logger.logInfo("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.logError("shootme: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.logInfo("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.logInfo("dialog terminated event recieved:" + e);
    }

    /** Call-IDs of the MESSAGEs that reached the listener */
    public List<String> getReceivedMessages() {
        synchronized (receivedMessages) {
            return new ArrayList<String>(receivedMessages);
        }
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public void stop() {
        sipStack.stop();
    }

    public TestAssertion getMessageReceivedAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !receivedMessages.isEmpty();
            }
        };
    }
}
