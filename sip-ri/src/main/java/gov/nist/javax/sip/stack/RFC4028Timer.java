package gov.nist.javax.sip.stack;

import java.io.Serializable;
import java.util.Iterator;

import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.TransactionState;
import javax.sip.header.ContentDispositionHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.ContentType;
import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.SessionExpires;
import gov.nist.javax.sip.message.Content;
import gov.nist.javax.sip.message.MultipartMimeContent;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

class RFC4028Timer extends SIPStackTimerTask implements Serializable  {
	
	private static StackLogger logger = CommonLogger.getLogger(RFC4028Timer.class);
	private String callId;
	private SIPDialog dialog;
	private static final long serialVersionUID = 1L;
	private int sessionExpires;
	private boolean useUpdate = false;
	private boolean sendRefresh = false;
	private volatile byte[] sdp;

	/*
	 * Use this one for when you ARE the refresher
	 */
	public RFC4028Timer(String callId, SIPDialog dialog, int sessionExpires, Boolean useUpdate) {
		super(RFC4028Timer.class.getSimpleName());
		this.callId = callId;
		this.dialog = dialog;
		this.sessionExpires = sessionExpires;
		if(useUpdate != null) {
			sendRefresh = true;
			this.useUpdate = useUpdate;
		}
		
	}
	
	/*
	 * Use this one for when you are NOT the refresher
	 */
	public RFC4028Timer(String callId, SIPDialog dialog, int sessionExpires) {
		this(callId, dialog, sessionExpires, null);
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
		if(dialog.RefreshSessionTask.get() != this || dialog.getState()==DialogState.TERMINATED) {
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
					if(useUpdate) {
						refreshRequest = dialog.createRequest(Request.UPDATE);
					}
					else {
						if(sdp==null && last!=null) {
							if(last instanceof SIPClientTransaction)
							{
								setSDPForReInviteRefresh((SIPMessage) last.getRequest());
							}
							else {
								setSDPForReInviteRefresh((SIPMessage) last.getResponse());
							}
						}
						ContentType ct = new ContentType("application", "sdp");
						if(sdp!=null) {
							refreshRequest = dialog.createRequest(Request.INVITE);
							refreshRequest.setContent(sdp, ct);
						}
					}
					//we SHOULD send reINVITE with SDP or UPDATE without one per RFC
					//if we don't have either, safe bet is to just let dialog die
					if(useUpdate || sdp!=null) {
						SessionExpires se = new SessionExpires();
						se.setExpires(sessionExpires);
						se.setRefresher("uac");
						refreshRequest.setHeader(se);
				
						MinSE minSE = new MinSE();
						minSE.setExpires(sessionExpires);
						refreshRequest.setHeader(minSE);
				
						ClientTransaction refreshCtx = dialog.getSipProvider().getNewClientTransaction(refreshRequest);
						dialog.sendRequest(refreshCtx);
					}
				
				}
				catch (Exception ex) {
					logger.logError("RFC 4028: could not refresh the session:" + dialog.getDialogId(), ex);
				}
			}
				//same as above, here we just make sure it's not going to get started
				if(dialog.RefreshSessionTask.get() == this) {
					//Reschedule the timer to timeout if we don't get a response in time
					sendRefresh = false;
					long deadlineMs = sessionExpires * 1000L - Math.min(32_000L, sessionExpires * 1000L / 3);
					dialog.getStack().getTimer().schedule(this, deadlineMs - sessionExpires * 1000L / 2);
				}
			}
			else {
				dialog.rfc4028SessionExpired(true);
				dialog.RefreshSessionTask.compareAndSet(this, null);
			}
	}
	/*
	 * Will only save SDP for ReINVITE refreshes that were not yet sent
	 * otherwise does nothing
	 */
	public void setSDPForReInviteRefresh(SIPMessage message) {
		if (message == null) 
			return;
		if(useUpdate==false && sendRefresh == true) {
			ContentType ct = message.getContentTypeHeader();
		    if (ct == null || message.getRawContent() == null) {
		        return;
		    }
		    if ("application".equalsIgnoreCase(ct.getContentType())
		            && "sdp".equalsIgnoreCase(ct.getContentSubType())) {
		        sdp = message.getRawContent();
		        return;
		    }
		    if ("multipart".equalsIgnoreCase(ct.getContentType())) {
		        try {  
		        	MultipartMimeContent mmc = message.getMultipartMimeContent();
		        	if (mmc != null) 
		        	{
		        		Iterator<Content> parts = mmc.getContents();
		        		while (parts.hasNext()) {
		        			Content part = parts.next();
		        			ContentTypeHeader pct = part.getContentTypeHeader();
		        			ContentDispositionHeader pcd = part.getContentDispositionHeader();
		        			if (pct != null && "application".equalsIgnoreCase(pct.getContentType()) 
		        					&& "sdp".equalsIgnoreCase(pct.getContentSubType()) && (pcd == null || "session".equalsIgnoreCase(pcd.getDispositionType())))
		        				{
		        					sdp = part.getContent();
		        					break;
		        				}
		        		}
		        	}
		        } catch (Exception ex) {
		        	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
						logger.logDebug("RFC 4028 timer couldn't extract SDP from:" + message);
					return;
		        }
		    }
		}
	}
}
