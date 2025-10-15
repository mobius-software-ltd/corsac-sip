package gov.nist.javax.sip.stack;

import java.io.Serializable;

import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.ObjectInUseException;
import javax.sip.TransactionState;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;

/**
 * This task waits till a pending ACK has been recorded and then sends out a
 * re-INVITE. This is to prevent interleaving INVITEs ( which will result in a
 * 493 from the UA that receives the out of order INVITE). This is primarily for
 * B2BUA support. A B2BUA may send a delayed ACK while it does mid call codec
 * renegotiation. In the meanwhile, it cannot send an intervening re-INVITE
 * otherwise the othr end will respond with a REQUEST_PENDING. We want to avoid
 * this condition. Hence we wait till the ACK for the previous re-INVITE has
 * been sent before sending the next re-INVITE.
 */
public class ReInviteSender implements SIPTask, Serializable {
	private static StackLogger logger = CommonLogger.getLogger(SIPDialog.class);

	private static final long serialVersionUID = 1019346148741070635L;
	ClientTransaction ctx;
	long startTime = System.currentTimeMillis();

	private String callId;
	private SIPDialog dialog;
	private String taskName;
	
	public void terminate() {
		try {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("ReInviteSender::terminate: ctx = " + ctx);
			}

			ctx.terminate();
			// Thread.currentThread().interrupt();
		} catch (ObjectInUseException e) {
			logger.logError("unexpected error", e);
		}
	}

	public ReInviteSender(ClientTransaction ctx, String callId, SIPDialog dialog, String taskName) {
		this.ctx = ctx;
		this.callId = callId;
		this.dialog = dialog;
		this.taskName = taskName;
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("ReInviteSender::ReInviteSender: ctx = " + ctx);
			logger.logStackTrace();
		}
	}

	@Override
	public void execute() {
		try {
			long timeToWait = 0;
			long startTime = System.currentTimeMillis();
			boolean dialogTimedOut = false;

			// If we have an INVITE transaction, make sure that it is TERMINATED
			// before sending a re-INVITE.. Not the cleanest solution but it works.
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("SIPDialog::reInviteSender: dialog = " + ctx.getDialog() + " lastTransaction = "
						+ dialog.getLastTransaction() + " lastTransactionState " + dialog.getLastTransaction().getState());
			}
			// if (SIPDialog.this.lastTransaction != null &&
			// SIPDialog.this.lastTransaction instanceof SIPServerTransaction &&
			// SIPDialog.this.lastTransaction.isInviteTransaction() &&
			// SIPDialog.this.lastTransaction.getState() != TransactionState.TERMINATED)
			// {
			// ((SIPServerTransaction)SIPDialog.this.lastTransaction).waitForTermination();
			// Thread.sleep(50);
			// }

			// if (!SIPDialog.this.takeAckSem()) {
			// /*
			// * Could not send re-INVITE fire a timeout on the INVITE.
			// */
			// if (logger.isLoggingEnabled())
			// logger
			// .logError(
			// "Could not send re-INVITE time out ClientTransaction");
			// ((SIPClientTransaction) ctx).fireTimeoutTimer();
			// /*
			// * Send BYE to the Dialog.
			// */
			// if (sipProvider.getSipListener() != null
			// && sipProvider.getSipListener() instanceof SipListenerExt) {
			// dialogTimedOut = true;
			// raiseErrorEvent(SIPDialogErrorEvent.DIALOG_REINVITE_TIMEOUT,(SIPClientTransaction)ctx);
			// } else {
			// Request byeRequest = SIPDialog.this
			// .createRequest(Request.BYE);
			// if (MessageFactoryImpl.getDefaultUserAgentHeader() != null) {
			// byeRequest.addHeader(MessageFactoryImpl
			// .getDefaultUserAgentHeader());
			// }
			// ReasonHeader reasonHeader = new Reason();
			// reasonHeader.setCause(1024);
			// reasonHeader.setText("Timed out waiting to re-INVITE");
			// byeRequest.addHeader(reasonHeader);
			// ClientTransaction byeCtx = SIPDialog.this
			// .getSipProvider().getNewClientTransaction(
			// byeRequest);
			// SIPDialog.this.sendRequest(byeCtx);
			// return;
			// }
			// }
			if (dialog.getState() != DialogState.TERMINATED) {

				timeToWait = System.currentTimeMillis() - startTime;
			}

			/*
			 * If we had to wait for ACK then wait for the ACK to actually get to the other
			 * side. Wait for any ACK retransmissions to finish. Then send out the request.
			 * This is a hack in support of some UA that want re-INVITEs to be spaced out in
			 * time ( else they return a 400 error code ).
			 */
			try {
				if (timeToWait != 0) {
					Thread.sleep(dialog.reInviteWaitTime);
				}
			} catch (InterruptedException ex) {
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
					logger.logDebug("Interrupted sleep");
				return;
			}
			if (dialog.getState() != DialogState.TERMINATED && !dialogTimedOut
					&& ctx.getState() != TransactionState.TERMINATED) {
				dialog.sendRequest(ctx, true);
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
					logger.logDebug("re-INVITE successfully sent");
			}

		} catch (Exception ex) {
			logger.logError("Error sending re-INVITE", ex);
		} finally {
			this.ctx = null;
		}
	}

	@Override
	public long getStartTime() {
		return startTime;
	}

	@Override
	public String getId() {
		return callId;
	}
	
	@Override
	public String printTaskDetails() {
		return "Task name: " + taskName + ", id: " + callId;
	}
}