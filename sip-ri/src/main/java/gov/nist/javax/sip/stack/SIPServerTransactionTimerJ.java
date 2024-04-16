package gov.nist.javax.sip.stack;

import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class SIPServerTransactionTimerJ extends SIPStackTimerTask {
    private static StackLogger logger = CommonLogger.getLogger(SIPServerTransactionTimerJ.class);    
    private SIPServerTransactionImpl serverTransaction;

    public SIPServerTransactionTimerJ(SIPServerTransactionImpl sipServerTransaction) {
        super(SIPServerTransactionImpl.TIMER_J_NAME);
        this.serverTransaction = sipServerTransaction;
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("TransactionTimerJ() : " + sipServerTransaction.getTransactionId());
        }
    }

    public void runTask() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("executing TransactionTimerJ() : " + serverTransaction.getTransactionId());
        }
        serverTransaction.fireTimeoutTimer();
        serverTransaction.cleanUp();
        if (serverTransaction.originalRequest != null) {
            serverTransaction.originalRequest.cleanUp();
        }
    }

    @Override
    public String getId() {
        Request request = serverTransaction.getRequest();
        if (request != null && request instanceof SIPRequest) {
            return ((SIPRequest) request).getCallIdHeader().getCallId();
        } else {
            return serverTransaction.originalRequestCallId;
        }
    }
};