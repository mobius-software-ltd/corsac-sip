package gov.nist.javax.sip.stack;

import java.io.Serializable;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class SIPTransactionRetransmissionTimerTask extends SIPStackTimerTask implements Serializable {
    private static StackLogger logger = CommonLogger.getLogger(SIPTransactionRetransmissionTimerTask.class);    
    private static final long serialVersionUID = 1L;
    protected SIPTransactionImpl transaction;
    protected int retransmissionTimerLastTickCount;
    public SIPTransactionRetransmissionTimerTask(SIPTransactionImpl transaction,int retransmissionTimerLastTickCount) {
        	super(SIPTransactionRetransmissionTimerTask.class.getSimpleName());
            this.transaction = transaction;
            this.retransmissionTimerLastTickCount = retransmissionTimerLastTickCount;
         }

    public void runTask() {
        // If I ACK has not been seen on Dialog,
        // resend last response.
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("Running transaction retransmission timer");
        
        transaction.enableRetransmissionTimer(retransmissionTimerLastTickCount * 2);
        transaction.fireRetransmissionTimer();
    }

	@Override
	public String getId() {
		return transaction.getTransactionId();
	}
}