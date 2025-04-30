package gov.nist.javax.sip.stack;

import java.io.Serializable;

import javax.sip.ClientTransaction;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.Reason;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

class DialogDeleteIfNoAckSentTask extends SIPStackTimerTask implements Serializable {
	private static StackLogger logger = CommonLogger.getLogger(SIPDialog.class);
	private SIPTransactionStack sipStack;
	private String callId;
	private String dialogId;
	private static final long serialVersionUID = 1L;
	long seqno;

	public DialogDeleteIfNoAckSentTask(SIPTransactionStack sipStack, String callId, String dialogId, long seqno) {
		super(DialogDeleteIfNoAckSentTask.class.getSimpleName());
		this.seqno = seqno;
		this.dialogId = dialogId;
		this.callId = callId;
		this.sipStack = sipStack;
	}

	@Override
	public String getId() {
		return callId;
	}

	public void runTask() {
		SIPDialog dialog = sipStack.getDialog(dialogId);
		if (dialog != null && dialog.highestSequenceNumberAcknowledged < seqno) {
			/*
			 * Did not send ACK so we need to delete the dialog. B2BUA NOTE: we may want to
			 * send BYE to the Dialog at this point. Do we want to make this behavior
			 * tailorable?
			 */
			dialog.dialogDeleteIfNoAckSentTask = null;
			if (!dialog.isBackToBackUserAgent) {
				if (logger.isLoggingEnabled())
					logger.logDebug("ACK Was not sent. killing dialog " + dialogId);
				if (((SipProviderImpl) dialog.getSipProvider()).getSipListener() instanceof SipListenerExt) {
					dialog.raiseErrorEvent(SIPDialogErrorEvent.DIALOG_ACK_NOT_SENT_TIMEOUT);
				} else {
					dialog.delete();
				}
			} else {
				if (logger.isLoggingEnabled())
					logger.logDebug("ACK Was not sent. Sending BYE " + dialogId);
				if (((SipProviderImpl) dialog.getSipProvider()).getSipListener() instanceof SipListenerExt) {
					dialog.raiseErrorEvent(SIPDialogErrorEvent.DIALOG_ACK_NOT_SENT_TIMEOUT);
				} else {

					/*
					 * Send BYE to the Dialog. This will be removed for the next spec revision.
					 */
					try {
						Request byeRequest = dialog.createRequest(Request.BYE);
						if (MessageFactoryImpl.getDefaultUserAgentHeader() != null) {
							byeRequest.addHeader(MessageFactoryImpl.getDefaultUserAgentHeader());
						}
						ReasonHeader reasonHeader = new Reason();
						reasonHeader.setProtocol("SIP");
						reasonHeader.setCause(1025);
						reasonHeader.setText("Timed out waiting to send ACK " + dialogId);
						byeRequest.addHeader(reasonHeader);
						ClientTransaction byeCtx = dialog.getSipProvider().getNewClientTransaction(byeRequest);
						dialog.sendRequest(byeCtx);
						return;
					} catch (Exception ex) {
						dialog.delete();
					}
				}
			}
		}
	}
}
