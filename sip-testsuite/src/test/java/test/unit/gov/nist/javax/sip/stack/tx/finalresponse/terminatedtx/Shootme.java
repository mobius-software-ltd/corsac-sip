package test.unit.gov.nist.javax.sip.stack.tx.finalresponse.terminatedtx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.IOExceptionEventExt;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * Dialog support off. Answers the REGISTER through the stx the stack made for it, as set by
 * {@link Flow}. Keeps the status of every response the stack reports as refused on a finalised stx
 * (FinalisedTransactionTriedResponding), and every other exception with its step.
 */
public class Shootme implements SipListener {

    /** what goes through the REGISTER stx */
    public enum Flow {
        /** 200 from processRequest, the 504 later through {@link Shootme#sendTimeout()} */
        NORMAL,
        /** 200 and 504 queued, then the stx terminated, all before the worker runs either */
        TERMINATED,
        /** two 504s queued back to back */
        DOUBLE_504
    }

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final String transport;
    private final Flow flow;

    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private volatile ServerTransaction registerStx;
    /** getState() right before the 504 of {@link #sendTimeout()} */
    private volatile TransactionState stateBeforeTimeout;
    private volatile boolean flowDone;
    /** status of each response the stack refused on the finalised stx, in order */
    private final List<Integer> refused = Collections.synchronizedList(new ArrayList<Integer>());
    /** "step: exception", in the order they were thrown */
    private final List<String> exceptions = Collections.synchronizedList(new ArrayList<String>());
    private volatile Throwable firstException;

    public Shootme(int port, String transport, Flow flow) {
        this.port = port;
        this.transport = transport;
        this.flow = flow;
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
        Request request = requestEvent.getRequest();
        logger.info("shootme:" + port + " received " + request.getMethod());
        if (!Request.REGISTER.equals(request.getMethod())) {
            return;
        }
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                try {
                    st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
                } catch (Exception ex) {
                    caught("getNewServerTransaction", ex);
                    return;
                }
            }
            registerStx = st;
            // all queued here, the worker only gets to them once processRequest returns
            switch (flow) {
            case NORMAL:
                send(st, Response.OK);
                break;
            case TERMINATED:
                send(st, Response.OK);
                send(st, Response.SERVER_TIMEOUT);
                try {
                    st.terminate();
                } catch (Exception ex) {
                    caught("terminate", ex);
                }
                break;
            case DOUBLE_504:
                send(st, Response.SERVER_TIMEOUT);
                send(st, Response.SERVER_TIMEOUT);
                break;
            }
        } catch (Exception ex) {
            caught("processRequest", ex);
        } finally {
            flowDone = true;
        }
    }

    /** NORMAL: the 504, after the 200 */
    public void sendTimeout() {
        ServerTransaction st = registerStx;
        stateBeforeTimeout = st.getState();
        send(st, Response.SERVER_TIMEOUT);
    }

    private void send(ServerTransaction st, int statusCode) {
        try {
            st.sendResponse(messageFactory.createResponse(statusCode, st.getRequest()));
        } catch (Exception ex) {
            caught("sendResponse(" + statusCode + ")", ex);
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
        if (e instanceof IOExceptionEventExt && ((IOExceptionEventExt) e)
                .getReason() == IOExceptionEventExt.Reason.FinalisedTransactionTriedResponding) {
            int statusCode = ((Response) ((IOExceptionEventExt) e).getMessage()).getStatusCode();
            logger.info("shootme:" + port + " stack refused " + statusCode + " on the finalised stx");
            refused.add(statusCode);
            return;
        }
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

    public List<Integer> getRefused() {
        synchronized (refused) {
            return new ArrayList<Integer>(refused);
        }
    }

    public TransactionState getStateBeforeTimeout() {
        return stateBeforeTimeout;
    }

    /** the flow as the UAS saw it, for failure messages */
    public String describe() {
        ServerTransaction st = registerStx;
        return transport + ", " + flow
                + (flow == Flow.NORMAL ? ", getState() before the 504 " + stateBeforeTimeout : "") + ", now "
                + (st != null ? st.getState() : "no stx") + ", refused " + getRefused() + ", exceptions "
                + getExceptions();
    }

    public TestAssertion getFlowDoneAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return flowDone;
            }
        };
    }

    /** the worker has sent the 200 */
    public TestAssertion getCompletedAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                ServerTransaction st = registerStx;
                return st != null && TransactionState.COMPLETED == st.getState();
            }
        };
    }

    public void stop() {
        sipStack.stop();
    }
}
