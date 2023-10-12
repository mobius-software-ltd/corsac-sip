package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.text.ParseException;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.MessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NettyMessageHandler extends SimpleChannelInboundHandler<byte[]> {
    private static StackLogger logger = CommonLogger.getLogger(NettyMessageHandler.class);

    private SIPTransactionStack sipStack;
    private NettyTCPMessageProcessor messageProcessor;
    private MessageParser smp = null;

    private ByteBuf buf;
    
    public NettyMessageHandler(NettyTCPMessageProcessor nettyTCPMessageProcessor) {
        this.messageProcessor = nettyTCPMessageProcessor;
        this.sipStack = messageProcessor.sipStack;
        this.smp = sipStack.getMessageParserFactory().createMessageParser(sipStack);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        buf = ctx.alloc().buffer(4); // (1)
    }
    
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        buf.release(); // (1)
        buf = null;
    }
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, byte[] msg) {        
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("received following message: \n" + new String(msg));
        }        

        SIPMessage sipMessage = null;
				
        try {
            // byte[] msgBytes = m.getBytes("UTF-8");
            sipMessage = smp.parseSIPMessage(msg, true, false, null);            
        } catch (ParseException e) {
            logger.logDebug(
                    "Parsing issue !  " + new String(msg.toString()) + " " + e.getMessage());
        }
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            logger.logDebug(sipMessage.toString());
        }
        NettyTCPMessageChannel nettyTCPMessageChannel = new NettyTCPMessageChannel(messageProcessor, ctx.channel());
        if(sipStack.getSelfRoutingThreadpoolExecutor() != null) {
            final String callId = sipMessage.getCallId().getCallId();
            
            try{                                                              
                if(callId == null || callId.trim().length() < 1) {
                    // http://code.google.com/p/jain-sip/issues/detail?id=18
                    // NIO Message with no Call-ID throws NPE
                    throw new IOException("received message with no Call-ID");
                }
                if(sipStack.sipEventInterceptor != null
            			// https://java.net/jira/browse/JSIP-503
                		&& sipMessage != null) {
            		sipStack.sipEventInterceptor.beforeMessage(sipMessage);
            	}

            	if(sipMessage != null) { // https://java.net/jira/browse/JSIP-503
            		nettyTCPMessageChannel.processMessage(sipMessage);
            	}
            // } catch (ParseException e) {
            // 	// https://java.net/jira/browse/JSIP-499 move the ParseException here so the finally block 
            // 	// is called, the semaphore released and map cleaned up if need be
            // 	if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
            // 		logger.logDebug("Problem parsing message " + msg.toString() + " " + e.getMessage());
            // 	}
    		}catch (Exception e) {
            	logger.logError("Error occured processing message " + msg.toString(), e);
                // We do not break the TCP connection because other calls use the same socket here
            } finally {            
                if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                	logger.logDebug("releasing semaphore for message " + sipMessage);
                }
                if(sipStack.sipEventInterceptor != null
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
        cause.printStackTrace();
        ctx.close();
    }
}
