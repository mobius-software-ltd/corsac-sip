/*
 * Mobius Software LTD
 * Copyright 2019 - 2023, Mobius Software LTD and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package gov.nist.javax.sip.stack.transports.processors.netty;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.RawMessageChannel;

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

