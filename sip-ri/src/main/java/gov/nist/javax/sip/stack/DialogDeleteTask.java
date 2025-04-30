package gov.nist.javax.sip.stack;

import java.io.Serializable;

import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

/**
 * This timer task is used to garbage collect the dialog after some time.
 * 
 */

class DialogDeleteTask extends SIPStackTimerTask implements Serializable {
	private static final long serialVersionUID = 1L;

	private String callId;
	private String dialogId;
	private SIPTransactionStack sipStack;
	
	DialogDeleteTask(SIPTransactionStack sipStack,String callId,String dialogId) {
		super(DialogDeleteTask.class.getSimpleName());
		this.callId = callId;
		this.dialogId = dialogId;
		this.sipStack = sipStack;
	}

	public void runTask() {
		SIPDialog dialog = sipStack.getDialog(dialogId);
		if (dialog != null)
			dialog.delete();
	}

	@Override
	public String getId() {
		return callId;
	}
}
