package gov.nist.javax.sip.stack;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.TransactionState;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentDispositionHeader;
import javax.sip.header.ContentEncodingHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ErrorInfoHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.MimeVersionHeader;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.RSeqHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RetryAfterHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ServerHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UnsupportedHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.header.WarningHeader;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.ContentType;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.extensions.JoinHeader;
import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.ReferredByHeader;
import gov.nist.javax.sip.header.extensions.ReplacesHeader;
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
	
	private Boolean sendRefresh = false;
	private volatile byte[] sdp;

	private volatile Map<String, List<SIPHeader>> headerTemplate;
	
	private transient ClientTransaction refreshCtx;
	
	/*
	 * Use this one for when you ARE the refresher
	 */
	public RFC4028Timer(SIPDialog dialog, int sessionExpires, Boolean useUpdate) {
		super(RFC4028Timer.class.getSimpleName());
		this.callId = dialog.getCallId().getCallId();
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
	public RFC4028Timer(SIPDialog dialog, int sessionExpires) {
		this(dialog, sessionExpires, null);
	}
	
	/*
	 * use this one when PRACK/100rel is present
	 * empty timer, will not be started 
	 */
	public RFC4028Timer(SIPDialog dialog) {
		this(dialog, 0, null);
		this.sendRefresh=null;
	}
	
	@Override
	public String getId() {
		return callId;
	}

	@Override
	public void runTask() {
		//Don't start empty timer
		if(sendRefresh==null) {
			return;
		}
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
					if(useUpdate) {
						//no point in saving it now
						sdp = null;
						refreshRequest = dialog.createRequest(Request.UPDATE);
						copyHeaderTemplate((SIPMessage) refreshRequest);
					}
					else {
						ContentType ct = new ContentType("application", "sdp");
						if(sdp!=null) {
							refreshRequest = dialog.createRequest(Request.INVITE);
							copyHeaderTemplate((SIPMessage) refreshRequest);
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
	 * Will only save SDP for ReINVITE refreshes that were not yet sent
	 * otherwise does nothing
	 * Completely null-safe btw
	 */
	public void setSDPForReInviteRefresh(SIPMessage message) {
		if (message == null || useUpdate == true) 
			return;
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
	
	//necessary for PRACK/100rel offer
	protected byte[] getSdp() {
		return sdp;
	}
	
	protected void setSdp(byte[] sdp) {
		if (this.sdp == null)
			this.sdp = sdp;
	}
	
	//useful for early UPDATE 
	protected boolean isUseUpdate() {
		return useUpdate;
	}
	
	protected void setUseUpdate(boolean useUpdate) {
		 this.useUpdate = useUpdate;
	}
	
	public boolean isTimerEmpty() {
		return sendRefresh==null;
	}
	
	public static Map<String, List<SIPHeader>> createHeaderMap(SIPMessage message)
	{
	    Map<String, List<SIPHeader>> headerMap = new HashMap<>();
	    ListIterator<String> names = message.getHeaderNames();
	    while (names.hasNext())
	    {
	        String name = names.next();
	        boolean isHeaderBasic = false;
	        switch (name)
	        {
	            case CallIdHeader.NAME:
	            case FromHeader.NAME:
	            case ToHeader.NAME:
	            case CSeqHeader.NAME:
	            case ViaHeader.NAME:
	            case MaxForwardsHeader.NAME:
	            case ContactHeader.NAME:
	            case RouteHeader.NAME:
	            case RecordRouteHeader.NAME:
	            case ContentLengthHeader.NAME:
	            case WWWAuthenticateHeader.NAME:
	            case ProxyAuthenticateHeader.NAME:
	            case AuthorizationHeader.NAME:
	            case ProxyAuthorizationHeader.NAME:
	            // the timer sets these itself
	            case SessionExpires.NAME:
	            case MinSE.NAME:
	            case ContentTypeHeader.NAME:
	            case ContentDispositionHeader.NAME:
	            case ContentEncodingHeader.NAME:
	            case MimeVersionHeader.NAME:
	            // 100rel must not be inherited by the refresh
	            case RequireHeader.NAME:
	            // initial INVITE only
	            case ExpiresHeader.NAME:
	            case ReplacesHeader.NAME:
	            case JoinHeader.NAME:
	            case ReferredByHeader.NAME:
	            // response only (UAS template is our 2xx)
	            case ServerHeader.NAME:
	            case WarningHeader.NAME:
	            case RSeqHeader.NAME:
	            case RetryAfterHeader.NAME:
	            case UnsupportedHeader.NAME:
	            case ErrorInfoHeader.NAME:
	                isHeaderBasic = true;
	                break;
	            default:
	                isHeaderBasic = false;
	                break;
	        }
	        if (isHeaderBasic)
	            continue;
	        ListIterator<SIPHeader> headers = message.getHeaders(name);
	        List<SIPHeader> headersList = new ArrayList<>();
	        while (headers.hasNext())
	        {
	            headersList.add((SIPHeader) headers.next().clone());
	        }
	        if (!headersList.isEmpty())
	            headerMap.put(name, headersList);
	    }
	    return headerMap;
	}

	//the map is reused for every refresh of this dialog, hence the clone on the way out
	private void copyHeaderTemplate(SIPMessage refreshRequest)
	{
	    if (headerTemplate == null)
	        return;
	    for (Map.Entry<String, List<SIPHeader>> entry : headerTemplate.entrySet())
	    {
	        refreshRequest.removeHeader(entry.getKey());
	        for (SIPHeader h : entry.getValue())
	        {
	            refreshRequest.addHeader((SIPHeader) h.clone());
	        }
	    }
	}
	
	//same as for SDP, convenience method to just pass the message
	protected void setHeaderTemplate(SIPMessage ours) {
	    if (ours != null)
	        this.headerTemplate = createHeaderMap(ours);
	}

	protected void setHeaderTemplate(Map<String, List<SIPHeader>> headerTemplate) {
	    if (this.headerTemplate == null)
	        this.headerTemplate = headerTemplate;
	}

	protected Map<String, List<SIPHeader>> getHeaderTemplate() {
	    return headerTemplate;
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
