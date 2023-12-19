package gov.nist.core.executor;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.RawMessageChannel;
import gov.nist.javax.sip.stack.SIPTransactionStack;

public class IncomingMessageProcessingTask implements SIPTask {
    private static StackLogger logger = CommonLogger.getLogger(IncomingMessageProcessingTask.class);
    private String id;
    private long startTime;
    private RawMessageChannel rawMessageChannel;
    private SIPMessage sipMessage;  
    private SIPTransactionStack sipStack;


    public IncomingMessageProcessingTask(RawMessageChannel rawMessageChannel, SIPMessage sipMessage) {
        startTime = System.currentTimeMillis();
        this.id = sipMessage.getCallId().getCallId();    
        this.rawMessageChannel = rawMessageChannel;
        this.sipMessage = sipMessage;  
        this.sipStack = rawMessageChannel.getSIPStack();
    }

    @Override
    public void execute() {        
        if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
            logger.logDebug("Executing task " + this + " with id: " + id);
        }
        if (sipStack.sipEventInterceptor != null) {
            if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                logger.logDebug("calling beforeMessage eventinterceptor for message " + sipMessage);
            }
            sipStack.sipEventInterceptor.beforeMessage(sipMessage);
        }
        try {
            rawMessageChannel.processMessage(sipMessage);
        } catch (Exception e) {
            logger.logError("Error occured processing message " + sipMessage.toString(), e);                
        } finally {                
            if (sipStack.sipEventInterceptor != null) {
                if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                    logger.logDebug("calling afterMessage eventinterceptor for message " + sipMessage);
                }
                sipStack.sipEventInterceptor.afterMessage(sipMessage);
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }    
}

