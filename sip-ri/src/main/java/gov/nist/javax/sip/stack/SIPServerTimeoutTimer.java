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
        if (serverTransaction.isTerminated()) {
            // Keep the transaction hanging around in the transaction table
            // to catch the incoming ACK -- this is needed for tcp only.
            // Note that the transaction record is actually removed in
            // the connection linger timer.
            serverTransaction.stopTimeoutTimer();

            // Oneshot timer that garbage collects the SeverTransaction
            // after a scheduled amount of time. The linger timer allows
            // the client side of the tx to use the same connection to
            // send an ACK and prevents a race condition for creation
            // of new server tx
            SIPStackTimerTask myTimer = serverTransaction.new LingerTimer();

            SIPTransactionStack sipStack = serverTransaction.getSIPStack();
            if (sipStack.getConnectionLingerTimer() != 0) {
                sipStack.getTimer().schedule(myTimer, sipStack.getConnectionLingerTimer() * 1000);
            } else {
                myTimer.runTask();
            }
        } else {
            // Add to the fire list -- needs to be moved
            // outside the synchronized block to prevent
            // deadlock.
            serverTransaction.fireTimeoutTimer();
        }
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
}
