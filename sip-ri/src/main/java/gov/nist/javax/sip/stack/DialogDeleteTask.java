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
	private SIPDialog dialog;
	
	DialogDeleteTask(String callId,SIPDialog dialog) {
		super(DialogDeleteTask.class.getSimpleName());
		this.callId = callId;
		this.dialog = dialog;
	}

	public void runTask() {
		dialog.delete();
	}

	@Override
	public String getId() {
		return callId;
	}
}
