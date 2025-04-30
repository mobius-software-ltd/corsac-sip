package gov.nist.javax.sip.stack;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPRequest;

public final class ACKWrapper {
	private static StackLogger logger = CommonLogger.getLogger(SIPDialog.class);

	private SIPTransactionStack sipStack;
    String msgBytes;
    String fromTag;
    String dialogId;
    Long cSeq;
    ACKWrapper(SIPTransactionStack sipStack,SIPRequest req) {
    	this.sipStack = sipStack;
        req.setTransaction(null); // null out the associated Tx (release memory)
        msgBytes = req.encode();
        fromTag=req.getFromTag();
        dialogId=req.getDialogId(false);
        
        if(req.getCSeq()!=null)
        	cSeq = req.getCSeq().getSeqNumber();
    }
    
    public ACKWrapper(SIPTransactionStack sipStack,String msgBytes, String fromTag, String dialogId, Long cSeq) {
    	this.sipStack = sipStack;
        this.msgBytes = msgBytes;
        this.fromTag = fromTag;
        this.dialogId = dialogId;
        this.cSeq = cSeq;
    }
    
    public String getFromTag() {
		return fromTag;
	}

	public Long getCSeq() {
		return cSeq;
	}

	public String getDialogId() {
		return dialogId;
	}

	public String getMsgBytes() {
		return msgBytes;
	}

	public SIPRequest reparseRequest() {
		try {
			return (SIPRequest) sipStack.getMessageParserFactory().createMessageParser(sipStack)
					.parseSIPMessage(msgBytes.getBytes("UTF-8"), true, false, null);
		} catch (Exception ex) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("SIPDialog::resendAck:lastAck failed reparsing, hence not resending ACK");
			}
			return null;
		}
	}
}