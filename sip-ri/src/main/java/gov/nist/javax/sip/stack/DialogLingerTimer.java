package gov.nist.javax.sip.stack;

import java.io.Serializable;

import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class DialogLingerTimer extends SIPStackTimerTask implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private SIPTransactionStack sipStack;
	private SipProviderImpl sipProvider;
	private SIPDialog dialog;
	private String callId;
	
	DialogLingerTimer(SIPTransactionStack sipStack, SipProviderImpl sipProvider, SIPDialog dialog,String callId){
		super(DialogLingerTimer.class.getSimpleName());
		this.sipStack = sipStack;
		this.sipProvider = sipProvider;
		this.dialog = dialog;
		this.callId = callId;
	}

    public void runTask() {
    	try {
			sipStack.removeDialog(dialog, sipProvider);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		// Issue 279 :
        // https://jain-sip.dev.java.net/issues/show_bug.cgi?id=279
        // if non reentrant listener is used the event delivery of
        // DialogTerminated
        // can happen after the clean
        if (((SipStackImpl) sipStack).isReEntrantListener()) {
             ((SIPDialog)dialog).cleanUp();
        }
    }

    @Override
    public String getId() {
        return callId;
    }
}