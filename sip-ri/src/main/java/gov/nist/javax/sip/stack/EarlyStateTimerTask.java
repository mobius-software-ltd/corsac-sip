package gov.nist.javax.sip.stack;

import java.io.Serializable;

import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

/**
 * This timer task is used to garbage collect the dialog after some time.
 * 
 */

class EarlyStateTimerTask extends SIPStackTimerTask implements Serializable {
	private static final long serialVersionUID = 1L;

	private String callId;
	private String dialogId;
	private SIPTransactionStack sipStack;
	
	EarlyStateTimerTask(SIPTransactionStack sipStack,String callId,String dialogId) {
		super(EarlyStateTimerTask.class.getSimpleName());
		this.callId = callId;
		this.dialogId = dialogId;
		this.sipStack = sipStack;
	}

	public void runTask() {
		SIPDialog dialog = sipStack.getDialog(dialogId);
		if (dialog != null)
			dialog.fireEarlyStateTimer();
	}

	@Override
	public String getId() {
		return callId;
	}
}
