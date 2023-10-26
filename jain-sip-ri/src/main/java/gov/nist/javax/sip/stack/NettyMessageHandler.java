package gov.nist.javax.sip.stack;

import java.io.IOException;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogLevels;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;

public class NettyMessageHandler extends ChannelInboundHandlerAdapter {
    private static StackLogger logger = CommonLogger.getLogger(NettyMessageHandler.class);

    private SIPTransactionStack sipStack;
    private NettyStreamMessageProcessor messageProcessor;

    public NettyMessageHandler(NettyStreamMessageProcessor nettyTCPMessageProcessor) {
        this.messageProcessor = nettyTCPMessageProcessor;
        this.sipStack = messageProcessor.sipStack;
    }   

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        SIPMessage sipMessage = (SIPMessage) msg;
        NettyStreamMessageChannel nettyTCPMessageChannel = messageProcessor.createMessageChannel(ctx.channel());
        
        if (sipMessage.isNullRequest()) {
            if (sipMessage.getSize() == 4) {
                    // Handling keepalive ping (double CRLF) as defined per RFC 5626 Section 4.4.1
                    // sending pong (single CRLF)
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug(
                                "KeepAlive Double CRLF received, sending single CRLF as defined per RFC 5626 Section 4.4.1");
                        logger.logDebug("~~~ setting isPreviousLineCRLF=false");
                    }

                try {
                    nettyTCPMessageChannel.sendSingleCLRF();
                } catch (Exception e) {
                    logger.logError("A problem occured while trying to send a single CLRF in response to a double CLRF", e);
                }
            } else {
                if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG)) {
                    logger.logDebug("Received CRLF, canceling ping keep alive timeout task if started");
                }

                nettyTCPMessageChannel.cancelPingKeepAliveTimeoutTaskIfStarted();
            }
            return;
        }

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
                    sipStack.sipEventInterceptor.beforeMessage(sipMessage);
                }

                if (sipMessage != null) { // https://java.net/jira/browse/JSIP-503
                    nettyTCPMessageChannel.processMessage(sipMessage);
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
                // We do not break the TCP connection because other calls use the same socket
                // here
            } finally {
                if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                    logger.logDebug("releasing semaphore for message " + sipMessage);
                }
                if (sipStack.sipEventInterceptor != null
                        // https://java.net/jira/browse/JSIP-503
                        && sipMessage != null) {
                    sipStack.sipEventInterceptor.afterMessage(sipMessage);
                }
            }
        } else {
            try {
                nettyTCPMessageChannel.processMessage(sipMessage);
            } catch (Exception e) {
                logger.logError("Can't process message " + msg.toString(), e);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            logger.logError("Read Timeout Received on channel " + ctx.channel() + ", closing channel", (Exception)cause.getCause());
            ctx.channel().close();            
         } else {
            logger.logError("Exception on channel " + ctx.channel() + ", closing channel handle context", (Exception)cause.getCause());
            ctx.close();
         }    
    }
}
