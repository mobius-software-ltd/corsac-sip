package gov.nist.javax.sip.stack;

import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class SIPServerTimeoutTimer extends SIPStackTimerTask {
    private static StackLogger logger = CommonLogger.getLogger(SIPServerTransaction.class);    
    private SIPServerTransactionImpl serverTransaction;

    public SIPServerTimeoutTimer(SIPServerTransactionImpl sipServerTransaction) {
        super(SIPServerTimeoutTimer.class.getSimpleName());
        this.serverTransaction = sipServerTransaction;
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("TransactionTimer() : " + serverTransaction.getTransactionId());
        }
    }

    public void runTask() {
        // If the transaction has terminated,
        if (!serverTransaction.isTerminated()) {
            serverTransaction.fireTimeoutTimer();
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
}
