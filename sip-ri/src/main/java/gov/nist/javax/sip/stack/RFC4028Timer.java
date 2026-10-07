package gov.nist.javax.sip.stack;

import java.io.Serializable;

import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.TransactionState;
import javax.sip.header.ContactHeader;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.ContentType;
import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.SessionExpires;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

class RFC4028Timer extends SIPStackTimerTask implements Serializable  {
	
	private static StackLogger logger = CommonLogger.getLogger(RFC4028Timer.class);
	private String callId;
	private SIPDialog dialog;
	private static final long serialVersionUID = 1L;
	private int sessionExpires;
	private boolean sendRefresh = false;
	private transient ClientTransaction refreshCtx;
	
	/*
	 * Use this one for when you ARE the refresher
	 */
	public RFC4028Timer(SIPDialog dialog, int sessionExpires, boolean sendRefresh) {
		super(RFC4028Timer.class.getSimpleName());
		this.callId = dialog.getCallId().getCallId();
		this.dialog = dialog;
		this.sessionExpires = sessionExpires;
		this.sendRefresh = sendRefresh;
		
	}
	
	/*
	 * Use this one for when you are NOT the refresher
	 */
	public RFC4028Timer(SIPDialog dialog, int sessionExpires) {
		this(dialog, sessionExpires, false);
	}
	
	@Override
	public String getId() {
		return callId;
	}

	@Override
	public void runTask() {
		
		//since refresher will be rescheduling itself
		//we need to make sure we aren't running 2 timers on accident
		//(if we processResponse of the answer and reschedule at the same time for example)
		if(dialog.refreshSessionTask.get() != this || dialog.getState()==DialogState.TERMINATED) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
				logger.logDebug("RFC 4028 timer had a duplicate or tried to run at a dead dialog:" + dialog.getDialogId());
			return;
		}
			if(sendRefresh)
			{	   
			//Just in case a refresh is already in flight
			SIPTransaction last = dialog.getLastTransaction();
			if(!(last!=null && last.isInviteTransaction() && last.getState() != TransactionState.COMPLETED && last.getState() != TransactionState.TERMINATED && last.getState() != TransactionState.CONFIRMED))
			{
			   
				Request refreshRequest = null;
				try {
					ContentType ct = new ContentType("application", "sdp");
					byte[] sdp = dialog.getSDPForReInviteRefresh();
					if(sdp!=null) {
						refreshRequest = dialog.createRequest(Request.INVITE);
						//re-INVITE is a target refresh: send the Contact the peer knows, not the listening point default
						if(dialog.getMyContactHeader()!=null)
							refreshRequest.setHeader((ContactHeader) dialog.getMyContactHeader().clone());
						refreshRequest.setContent(sdp, ct);
					}
					//we SHOULD send reINVITE with SDP per RFC
					//if we don't have one, safe bet is to just let dialog die
					if(sdp!=null) {
						SessionExpires se = new SessionExpires();
						se.setExpires(sessionExpires);
						se.setRefresher("uac");
						refreshRequest.setHeader(se);
				
						MinSE minSE = new MinSE();
						minSE.setExpires(sessionExpires);
						refreshRequest.setHeader(minSE);
				
						ClientTransaction refreshCtx = dialog.getSipProvider().getNewClientTransaction(refreshRequest);
						dialog.sendRequest(refreshCtx);
						this.refreshCtx = refreshCtx;
					}
				
				}
				catch (Exception ex) {
					logger.logError("RFC 4028: could not refresh the session:" + dialog.getDialogId(), ex);
				}
			}
				//same as above, here we just make sure it's not going to get started
				if(dialog.refreshSessionTask.get() == this) {
					//Reschedule the timer to timeout if we don't get a response in time
					sendRefresh = false;
					long deadline = sessionExpires * 1000L - Math.min(32_000L, sessionExpires * 1000L / 3);
					dialog.getStack().getTimer().schedule(this, deadline - sessionExpires * 1000L / 2);
				}
			}
			else {
				dialog.refreshSessionTask.compareAndSet(this, null);
				closePendingRefresh();
				dialog.rfc4028SessionExpired();
			}
	}
	
	/*
	 * The refresh nobody answered is the last transaction of the dialog.
	 * Instead of having stack retransmit it and get 4xx, close prematurely
	 */
	private void closePendingRefresh() {
	    if (refreshCtx == null || refreshCtx.getState() == TransactionState.COMPLETED
	            || refreshCtx.getState() == TransactionState.TERMINATED)
	        return;
	    try {
	        if (refreshCtx.getRequest().getMethod().equals(Request.INVITE)
	                && refreshCtx.getState() == TransactionState.PROCEEDING) {
	            dialog.getSipProvider().getNewClientTransaction(refreshCtx.createCancel()).sendRequest();
	        } else {
	        	refreshCtx.terminate();
	        }
	    } catch (Exception ex) {
	        logger.logError("RFC 4028: could not close the pending refresh of " + dialog.getDialogId(), ex);
	    }
	}
}
