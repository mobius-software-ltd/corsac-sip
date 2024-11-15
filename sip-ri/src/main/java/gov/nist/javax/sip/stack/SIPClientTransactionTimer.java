package gov.nist.javax.sip.stack;

import javax.sip.Timeout;
import javax.sip.TimeoutEvent;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class SIPClientTransactionTimer extends SIPStackTimerTask {
    private static StackLogger logger = CommonLogger.getLogger(SIPClientTransactionTimer.class);    
    private SIPClientTransactionImpl clientTransaction;

    public SIPClientTransactionTimer(SIPClientTransactionImpl sipClientTransaction) {
        super(SIPClientTransactionTimer.class.getSimpleName());
        this.clientTransaction = sipClientTransaction;
    }

    public void runTask() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("running transaction timer  = " + clientTransaction.getTransactionId() +
                    ", isTerminated " + clientTransaction.isTerminated());
        }
        // If the transaction has terminated,
        if (clientTransaction.isTerminated()) {

            try {
                clientTransaction.stopTimeoutTimer();

            } catch (IllegalStateException ex) {
                if (!clientTransaction.getSIPStack().isAlive())
                    return;
            }

            clientTransaction.cleanUpOnTerminated();

        } else {
            SipProviderImpl provider = clientTransaction.getSipProvider();

            // This is a User Agent. The user has specified an Expires time. Start a timer
            // which will check if the tx is terminated by that time.
            if (clientTransaction.getDefaultDialog() != null && clientTransaction.isInviteTransaction() && clientTransaction.expiresTime != -1
                    && clientTransaction.expiresTime < System.currentTimeMillis()) {
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug("Expires time has been reached for the transaction " + clientTransaction.getTransactionId());
                }
                TimeoutEvent tte = new TimeoutEvent(provider, clientTransaction, Timeout.TRANSACTION);
                provider.handleEvent(tte, clientTransaction);
            }
            // If this transaction has not
            // terminated,
            // Fire the transaction timer.
            clientTransaction.fireTimeoutTimer();
        }
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