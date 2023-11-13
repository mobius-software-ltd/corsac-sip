/*
 * Mobius Software LTD
 * Copyright 2023, Mobius Software LTD and individual contributors
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
package gov.nist.javax.sip.stack;

import java.io.IOException;

import javax.sip.ListeningPoint;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogLevels;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;

/**
 * Netty Based Handler to handle processing of incoming SIP Messages
 * 
 * @author Jean Deruelle
 */
public class NettyMessageHandler extends ChannelInboundHandlerAdapter {
    private static StackLogger logger = CommonLogger.getLogger(NettyMessageHandler.class);

    private SIPTransactionStack sipStack;
    private NettyMessageProcessor messageProcessor;
    private boolean reliableTransport;

    public NettyMessageHandler(NettyMessageProcessor nettyMessageProcessor) {
        this.messageProcessor = nettyMessageProcessor;
        this.sipStack = messageProcessor.getSIPStack();
        this.reliableTransport = messageProcessor.getTransport().equalsIgnoreCase(ListeningPoint.TCP)
                || messageProcessor.getTransport().equalsIgnoreCase(ListeningPoint.TLS);
    }   

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        SIPMessage sipMessage = (SIPMessage) msg;
        Channel channel = ctx.channel();
        MessageChannel nettyMessageChannel = messageProcessor.createMessageChannel(channel);                 
        
        if (reliableTransport) {
            boolean continueProcesing = processReliableMessage(ctx, sipMessage, nettyMessageChannel);
            if(!continueProcesing) {
                return;
            }
        } 

        RawMessageChannel rawMessageChannel = (RawMessageChannel)nettyMessageChannel;
        if (sipStack.getSelfRoutingThreadpoolExecutor() != null) {
            final String callId = sipMessage.getCallId().getCallId();

            try {
                if (callId == null || callId.trim().length() < 1) {
                    // http://code.google.com/p/jain-sip/issues/detail?id=18
                    // NIO Message with no Call-ID throws NPE
                    throw new IOException("received message with no Call-ID");
                }
                if (sipStack.sipEventInterceptor != null
                        // https://java.net/jira/browse/JSIP-503
                        && sipMessage != null) {
                    if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                        logger.logDebug("calling beforeMessage eventinterceptor for message " + sipMessage);
                    }
                    sipStack.sipEventInterceptor.beforeMessage(sipMessage);
                }

                if (sipMessage != null) { // https://java.net/jira/browse/JSIP-503
                    rawMessageChannel.processMessage(sipMessage);
                }
                // } catch (ParseException e) {
                // // https://java.net/jira/browse/JSIP-499 move the ParseException here so the
                // finally block
                // // is called, the semaphore released and map cleaned up if need be
                // if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                // logger.logDebug("Problem parsing message " + msg.toString() + " " +
                // e.getMessage());
                // }
            } catch (Exception e) {
                logger.logError("Error occured processing message " + msg.toString(), e);                
            } finally {                
                if (sipStack.sipEventInterceptor != null
                        // https://java.net/jira/browse/JSIP-503
                        && sipMessage != null) {
                    if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                        logger.logDebug("calling afterMessage eventinterceptor for message " + sipMessage);
                    }
                    sipStack.sipEventInterceptor.afterMessage(sipMessage);
                }
            }
        } else {
            try {
                rawMessageChannel.processMessage(sipMessage);
            } catch (Exception e) {
                logger.logError("Can't process message " + msg.toString(), e);
            }
        }
    }

    private boolean processReliableMessage(ChannelHandlerContext ctx, SIPMessage sipMessage,
            MessageChannel nettyMessageChannel) {
        if(sipMessage.isNullRequest()) {
            NettyStreamMessageChannel nettyStreamMessageChannel = ((NettyStreamMessageChannel)nettyMessageChannel);
            if (sipMessage.getSize() == 4) {
                    // Handling keepalive ping (double CRLF) as defined per RFC 5626 Section 4.4.1
                    // sending pong (single CRLF)
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug(
                                "KeepAlive Double CRLF received, sending single CRLF as defined per RFC 5626 Section 4.4.1");
                        logger.logDebug("~~~ setting isPreviousLineCRLF=false");
                    }

                try {
                    nettyStreamMessageChannel.sendSingleCLRF();
                } catch (Exception e) {
                    logger.logError("A problem occured while trying to send a single CLRF in response to a double CLRF", e);
                }
            } else {
                if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG)) {
                    logger.logDebug("Received CRLF, canceling ping keep alive timeout task if started");
                }

                nettyStreamMessageChannel.cancelPingKeepAliveTimeoutTaskIfStarted();
            }
            return false;
        }
        return true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // cause.printStackTrace();
        if (cause instanceof ReadTimeoutException) {
            logger.logError("Read Timeout Received on channel " + ctx.channel() + ", closing channel", (Exception)cause.getCause());
            ctx.channel().close();            
         } else {
            logger.logError("Exception on channel " + ctx.channel() + ", closing channel handle context", (Exception)cause.getCause());
            ctx.close();
         }    
    }
}
