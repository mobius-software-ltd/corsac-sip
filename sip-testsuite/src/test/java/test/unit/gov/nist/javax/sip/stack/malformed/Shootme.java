package test.unit.gov.nist.javax.sip.stack.malformed;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

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
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import gov.nist.javax.sip.header.ims.PPreferredIdentityHeader;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.unit.gov.nist.javax.sip.stack.malformed.Records.MessageRecord;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Stands in for the RA: malformed identity header gets 400 (RFC 3261 21.4.1), any other INVITE INVITE_ANSWER.
 */
public class Shootme implements SipListener {

    private static final StackLogger logger = CommonLogger.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    /** non-2xx, no dialog to tear down */
    static final int INVITE_ANSWER = Response.BUSY_HERE;

    private final int port;

    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;
    private final RecordingStackLogger stackLogger;

    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());

    private volatile boolean ioExceptionSeen;

    /** transport set here, -DenableNetty / -DenableNIO don't apply */
    public Shootme(int port, String messageProcessorFactory) {
        this.port = port;
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", RecordingStackLogger.class.getName());
        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", messageProcessorFactory);
        // TRACE_LEVEL=LOG4J in an earlier test turns this off for the whole JVM, RecordingStackLogger would be skipped
        CommonLogger.useLegacyLogger = true;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, true, false,
                false, properties);
        this.stackLogger = (RecordingStackLogger) CommonLogger.legacyLogger;
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
        MessageRecord record = Records.record(request);
        receivedRequests.add(record);
        logger.logInfo("shootme:" + port + " received " + record);
        if (!request.getMethod().equals(Request.INVITE)) {
            return;
        }
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            String malformed = malformedIdentityHeader(record);
            Response response;
            if (malformed != null) {
                response = messageFactory.createResponse(Response.BAD_REQUEST, request);
                response.setReasonPhrase("Malformed " + malformed + " header field");
            } else {
                response = messageFactory.createResponse(INVITE_ANSWER, request);
            }
            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(Integer.toHexString(new Random().nextInt()));
            logger.logInfo("shootme:" + port + " answering INVITE with " + response.getStatusCode() + " "
                    + response.getReasonPhrase());
            st.sendResponse(response);
        } catch (Exception ex) {
            logger.logError("shootme: could not answer INVITE", ex);
        }
    }

    /** identity header left unparsed by the stack, null if none */
    private static String malformedIdentityHeader(MessageRecord request) {
        for (String header : request.unparsedHeaders) {
            String name = header.substring(0, Math.max(header.indexOf(':'), 0)).trim();
            if (name.equalsIgnoreCase(PPreferredIdentityHeader.NAME) || name.equalsIgnoreCase(PAssertedIdentityHeader.NAME)) {
                return name;
            }
        }
        return null;
    }

    public void processResponse(ResponseEvent responseEvent) {
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

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public List<MessageRecord> getReceivedInvites() {
        return Records.requests(receivedRequests, Request.INVITE);
    }

    /** ParseExceptions logged by the stack */
    public List<Throwable> getLoggedParseExceptions() {
        List<Throwable> out = new ArrayList<Throwable>();
        for (Throwable error : stackLogger.getErrors()) {
            if (error instanceof ParseException) {
                out.add(error);
            }
        }
        return out;
    }

    public void stop() {
        sipStack.stop();
    }
}
