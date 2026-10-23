package test.unit.gov.nist.javax.sip.stack.registerflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
  dialog support off: gets the REGISTER's stx with getNewServerTransaction,
 * on the request it was handed or on a clone of it, right away or later from another thread,
 * then sends a 200 and a 504 on it. Keeps every exception with the step that threw it.
 */
public class Shootme implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    /** later: well after processRequest returned*/
    private static final long LATER_DELAY = 500;

    private final int port;
    private final String transport;
    /** getNewServerTransaction on a clone instead of the request the stack delivered */
    private final boolean clone;
    /** getNewServerTransaction and both finals from a timer thread instead of processRequest */
    private final boolean later;

    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private final Timer timer = new Timer();

    private volatile ServerTransaction eventStx;
    private volatile ServerTransaction registerStx;
    private volatile boolean flowDone;
    /** "step: exception", in the order they were thrown */
    private final List<String> exceptions = Collections.synchronizedList(new ArrayList<String>());
    private volatile Throwable firstException;

    public Shootme(int port, String transport, boolean clone, boolean later) {
        this.port = port;
        this.transport = transport;
        this.clone = clone;
        this.later = later;
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme-" + port, "gov.nist", transport, false, false,
                false);
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
        final Request request = requestEvent.getRequest();
        logger.info("shootme:" + port + " received " + request.getMethod());
        if (!Request.REGISTER.equals(request.getMethod())) {
            return;
        }
        eventStx = requestEvent.getServerTransaction();
        final SipProvider provider = (SipProvider) requestEvent.getSource();
        if (later) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    answer(provider, request);
                }
            }, LATER_DELAY);
        } else {
            answer(provider, request);
        }
    }

    private void answer(SipProvider provider, Request request) {
        try {
            // a clone has no link to the stx the stack made: getNewServerTransaction builds its own
            Request target = clone ? (Request) request.clone() : request;
            ServerTransaction st;
            try {
                st = provider.getNewServerTransaction(target);
            } catch (Exception ex) {
                caught("getNewServerTransaction", ex);
                return;
            }
            registerStx = st;
            try {
                st.sendResponse(messageFactory.createResponse(Response.OK, request));
            } catch (Exception ex) {
                caught("sendResponse(200)", ex);
            }
            try {
                st.sendResponse(messageFactory.createResponse(Response.SERVER_TIMEOUT, request));
            } catch (Exception ex) {
                caught("sendResponse(504)", ex);
            }
        } catch (Exception ex) {
            caught("answer", ex);
        } finally {
            flowDone = true;
        }
    }

    private void caught(String step, Throwable ex) {
        logger.info("shootme:" + port + " " + step + " threw " + ex);
        exceptions.add(step + ": " + ex);
        if (firstException == null) {
            firstException = ex;
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        logger.info("shootme:" + port + " unexpected response " + responseEvent.getResponse().getStatusCode());
    }

    public void processTimeout(TimeoutEvent e) {
        logger.info("timeout event:" + e);
    }

    public void processIOException(IOExceptionEvent e) {
        caught("IOExceptionEvent", new java.io.IOException(e.getHost() + ":" + e.getPort() + "/" + e.getTransport()));
    }

    public void processTransactionTerminated(TransactionTerminatedEvent e) {
        logger.info("Transaction terminated event recieved:" + e);
    }

    public void processDialogTerminated(DialogTerminatedEvent e) {
        logger.info("dialog terminated event recieved:" + e);
    }

    public List<String> getExceptions() {
        synchronized (exceptions) {
            return new ArrayList<String>(exceptions);
        }
    }

    public Throwable getFirstException() {
        return firstException;
    }

    /**  failure details */
    public String describe() {
        ServerTransaction st = registerStx;
        return transport + ", " + (clone ? "clone" : "delivered request") + (later ? " later" : " in processRequest")
                + ", stx in the RequestEvent " + (eventStx != null ? "present" : "null") + ", stx "
                + (st != null ? "state now " + st.getState() : "none") + ", server tx table "
                + getServerTransactionTableSize() + ", exceptions " + getExceptions();
    }

    public TestAssertion getFlowDoneAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return flowDone;
            }
        };
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

    public void stop() {
        timer.cancel();
        sipStack.stop();
    }
}
