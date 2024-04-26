package gov.nist.javax.sip.stack;

import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class ClientTransactionTimerK extends SIPStackTimerTask {
    private static StackLogger logger = CommonLogger.getLogger(SIPServerTransactionTimerJ.class);    
    private SIPClientTransactionImpl clientTransaction;

    public ClientTransactionTimerK(SIPClientTransactionImpl sipClientTransaction) {
        super(SIPClientTransactionImpl.TIMER_K);
        this.clientTransaction = sipClientTransaction;
    }

    public void runTask() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("executing TransactionTimerJ() : " + clientTransaction.getTransactionId());
        }
        clientTransaction.fireTimeoutTimer();
        clientTransaction.cleanUpOnTerminated();
    }

    @Override
    public String getId() {
        Request request = clientTransaction.getRequest();
        if (request != null && request instanceof SIPRequest) {
            return ((SIPRequest) request).getCallIdHeader().getCallId();
        } else {
            return clientTransaction.originalRequestCallId;
        }
    }
}