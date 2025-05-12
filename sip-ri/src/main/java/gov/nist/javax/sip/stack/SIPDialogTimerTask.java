package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.io.Serializable;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class SIPDialogTimerTask extends SIPStackTimerTask implements Serializable {
	private static StackLogger logger = CommonLogger.getLogger(SIPDialogTimerTask.class);
	private static final long serialVersionUID = 1L;
	protected SIPDialog dialog;
	protected int timerT2;
	protected long baseTimerInterval;
	protected long currentInterval;
	protected long summaryInterval;
	protected SIPServerTransaction originalTransaction;

	public SIPDialogTimerTask(SIPDialog sipDialog, SIPServerTransaction originalTransaction, int timerT2,
			long baseTimerInterval, long currentInterval, long summaryInterval) {
		super(SIPDialogTimerTask.class.getSimpleName());
		this.dialog = sipDialog;
		this.timerT2 = timerT2;
		this.baseTimerInterval = baseTimerInterval;
		this.currentInterval = currentInterval;
		this.summaryInterval = summaryInterval + currentInterval;
		this.originalTransaction = originalTransaction;
	}

	public void runTask() {
		// If I ACK has not been seen on Dialog,
		// resend last response.
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
			logger.logDebug("Running dialog timer");

		currentInterval *= 2;

		SIPServerTransaction transaction = null;
		if (this.originalTransaction != null)
			transaction = (SIPServerTransaction) dialog.getStack()
					.findTransaction(this.originalTransaction.getTransactionId(), true);
		/*
		 * Issue 106. Section 13.3.1.4 RFC 3261 The 2xx response is passed to the
		 * transport with an interval that starts at T1 seconds and doubles for each
		 * retransmission until it reaches T2 seconds If the server retransmits the 2xx
		 * response for 64T1 seconds without receiving an ACK, the dialog is confirmed,
		 * but the session SHOULD be terminated.
		 */

		logger.logDebug(getId());
		if (summaryInterval > dialog.getStack().getAckTimeoutFactor() * SIPTransaction.T1 * baseTimerInterval) {
			if (dialog.getSipProvider().getSipListener() != null
					&& dialog.getSipProvider().getSipListener() instanceof SipListenerExt) {
				dialog.raiseErrorEvent(SIPDialogErrorEvent.DIALOG_ACK_NOT_RECEIVED_TIMEOUT);
				return;
			} else {
				dialog.delete();
			}
			if (transaction != null && transaction.getState() != javax.sip.TransactionState.TERMINATED) {
				transaction.raiseErrorEvent(SIPTransactionErrorEvent.TIMEOUT_ERROR);
			}
		} else if ((transaction != null || originalTransaction != null) && (!dialog.isAckSeen())) {
			// Retransmit to 2xx until ack receivedialog.
			if (dialog.lastResponseStatusCode.intValue() / 100 == 2) {
				try {

					// resend the last response.
					if (transaction == null)
						transaction = originalTransaction;
						
					transaction.resendLastResponse();					
				} catch (IOException ex) {

					dialog.raiseIOException(transaction.getLastResponse(),
							gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionError, transaction.getHost(),
							transaction.getPort(), transaction.getPeerAddress(), transaction.getPeerPort(),
							transaction.getPeerProtocol());

				} catch (MessageTooLongException ex) {

					dialog.raiseIOException(transaction.getLastResponse(),
							gov.nist.javax.sip.IOExceptionEventExt.Reason.MessageToLong, transaction.getHost(),
							transaction.getPort(), transaction.getPeerAddress(), transaction.getPeerPort(),
							transaction.getPeerProtocol());

				} finally {
					if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
						logger.logDebug("resend 200 response from " + dialog);
					}
				}
			}
		}
		
		if(transaction!=null)
			originalTransaction = transaction;

		// Stop running this timer if the dialog is in the
		// confirmed state or ack seen if retransmit filter on.
		if (dialog.isAckSeen() || dialog.dialogState.get() == SIPDialog.TERMINATED_STATE) {
			dialog.ongoingTransactionId = null;
			dialog.stopDialogTimer();
		} else {
			if (currentInterval <= timerT2 * baseTimerInterval)
				dialog.rescheduleDialogTimer(timerT2, baseTimerInterval, currentInterval, summaryInterval, originalTransaction);
			else
				dialog.rescheduleDialogTimer(timerT2, baseTimerInterval, timerT2 * baseTimerInterval, summaryInterval,
						originalTransaction);
		}
	}

	@Override
	public void cleanUpBeforeCancel() {
		dialog.ongoingTransactionId = null;
		// lastAckSent = null;
		dialog.cleanUpOnAck();
		super.cleanUpBeforeCancel();
	}

	@Override
	public String getId() {
		return dialog.getCallId().getCallId();
	}
}
