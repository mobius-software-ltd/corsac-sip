package test.unit.gov.nist.javax.sip.stack.retransmissiontimer;

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
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.SipStackImpl;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;
import test.unit.gov.nist.javax.sip.stack.retransmissiontimer.Records.MessageRecord;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Answers every new request with finalResponse right away, no provisional first.
 */
public class Shootme implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    private static final String transport = "udp";

    private final int port;

    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    /** Final response to every new request; 0 leaves them unanswered. */
    public int finalResponse = Response.OK;
    /** T1 in ms for our server transactions; 50 brings Timer H down to 3.2 s, T2 to 400 ms. */
    public int retransmitTimer = 50;

    private volatile ServerTransaction serverTransaction;

    private final List<MessageRecord> receivedRequests = Collections.synchronizedList(new ArrayList<MessageRecord>());

    private volatile boolean ioExceptionSeen;

    public Shootme(int port) {
        this.port = port;
        // NOTIFY arrives without a SUBSCRIBE behind it
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, false, false,
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
        MessageRecord record = Records.record(request);
        receivedRequests.add(record);
        logger.info("shootme:" + port + " received " + record);
        if (request.getMethod().equals(Request.ACK) || finalResponse == 0
                || requestEvent.getServerTransaction() != null) {
            return;
        }
        try {
            ServerTransaction st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
            st.setRetransmitTimer(retransmitTimer);
            serverTransaction = st;
            st.sendResponse(messageFactory.createResponse(finalResponse, request));
        } catch (Exception ex) {
            logger.error("shootme: could not answer " + record, ex);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        logger.info("shootme:" + port + " ignoring " + Records.record(responseEvent.getResponse()));
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        ioExceptionSeen = true;
        logger.error("shootme: IOException:" + e);
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public ServerTransaction getServerTransaction() {
        return serverTransaction;
    }

    public List<MessageRecord> getReceivedRequests(String method) {
        return Records.requests(receivedRequests, method);
    }

    public boolean isIoExceptionSeen() {
        return ioExceptionSeen;
    }

    public TestAssertion getServerTransactionAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return serverTransaction != null;
            }
        };
    }

    public void stop() {
        sipStack.stop();
    }

    public int getClientTransactionTableSize() {
        return ((SipStackImpl) sipStack).getClientTransactionTableSize();
    }

    public int getServerTransactionTableSize() {
        return ((SipStackImpl) sipStack).getServerTransactionTableSize();
    }

    public TestAssertion getNoTransactionsAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return getClientTransactionTableSize() == 0 && getServerTransactionTableSize() == 0;
            }
        };
    }
}
