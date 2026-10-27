package test.unit.gov.nist.javax.sip.stack.queuedfinals;

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

import gov.nist.javax.sip.stack.SIPServerTransaction;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAS template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 */
public class Shootme implements SipListener {

    private static final Logger logger = LogManager.getLogger(Shootme.class);
    private static final String myAddress = "127.0.0.1";
    /** a transaction's state before processRequest has run on it, getState() is null */
    private static final int UNSET = -1;

    private final int port;
    private final String transport;
    /** the 200 through SipProvider.sendResponse instead of the stx */
    public volatile boolean okStateless;
    /** the 504 through SipProvider.sendResponse instead of the stx */
    public volatile boolean timeoutStateless;
    /** stx set to unset before the two finals */
    public volatile boolean unset;
    /** stx terminated once both finals are queued, as an application cleanup would */
    public volatile boolean terminateAfter;

    private final MessageFactory messageFactory;
    private final SipStack sipStack;
    private SipProvider sipProvider;

    private volatile TransactionState stateBeforeFinals;
    private volatile ServerTransaction registerStx;
    private volatile boolean flowDone;
    /** "step: exception", in the order they were thrown */
    private final List<String> exceptions = Collections.synchronizedList(new ArrayList<String>());
    private volatile Throwable firstException;

    public Shootme(int port, String transport) {
        this.port = port;
        this.transport = transport;
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
            // fully stateless: no stx claimed at all
            if (st == null && !(okStateless && timeoutStateless)) {
                try {
                    st = ((SipProvider) requestEvent.getSource()).getNewServerTransaction(request);
                } catch (Exception ex) {
                    caught("getNewServerTransaction", ex);
                    return;
                }
            }
            registerStx = st;
            if (unset && st != null) {
                ((SIPServerTransaction) st).setState(UNSET);
            }
            stateBeforeFinals = st != null ? st.getState() : null;
            // both queued here, the worker only gets to them once processRequest returns
            send(st, messageFactory.createResponse(Response.OK, request), okStateless);
            send(st, messageFactory.createResponse(Response.SERVER_TIMEOUT, request), timeoutStateless);
            if (terminateAfter && st != null) {
                try {
                    st.terminate();
                } catch (Exception ex) {
                    caught("terminate", ex);
                }
            }
        } catch (Exception ex) {
            caught("processRequest", ex);
        } finally {
            flowDone = true;
        }
    }

    /** through the stx, or statelessly through the provider */
    private void send(ServerTransaction st, Response response, boolean stateless) {
        String step = (stateless ? "sipProvider" : "stx") + ".sendResponse(" + response.getStatusCode() + ")";
        try {
            if (stateless) {
                sipProvider.sendResponse(response);
            } else {
                st.sendResponse(response);
            }
        } catch (Exception ex) {
            caught(step, ex);
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

    /** the flow as the UAS saw it, for failure messages */
    public String describe() {
        ServerTransaction st = registerStx;
        return transport + ", 200 via " + (okStateless ? "provider" : "stx") + ", 504 via "
                + (timeoutStateless ? "provider" : "stx") + (unset ? ", stx set to unset" : "")
                + (terminateAfter ? ", stx terminated after both" : "")
                + ", getState() before the finals "
                + stateBeforeFinals + ", now " + (st != null ? st.getState() : "no stx") + ", exceptions "
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

    public void stop() {
        sipStack.stop();
    }
}
