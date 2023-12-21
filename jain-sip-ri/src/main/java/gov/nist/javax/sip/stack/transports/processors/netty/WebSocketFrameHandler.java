package gov.nist.javax.sip.stack.transports.processors.netty;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.IncomingMessageProcessingTask;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.NettyMessageParser;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.RawMessageChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static StackLogger logger = CommonLogger.getLogger(WebSocketFrameHandler.class);
    private SIPTransactionStack sipStack;
    private NettyMessageProcessor nettyMessageProcessor;

    public WebSocketFrameHandler(NettyMessageProcessor nettyMessageProcessor) {
        this.nettyMessageProcessor = nettyMessageProcessor;
        this.sipStack = nettyMessageProcessor.getSIPStack();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        
        // Send the uppercase string back.
        
        NettyMessageParser nettyMessageParser = new NettyMessageParser(                
                nettyMessageProcessor.getSIPStack().getMaxMessageSize(),
                nettyMessageProcessor.getSIPStack().isComputeContentLengthFromMessage());
        SIPMessage sipMessage = null;  
        ByteBuf content = null;
        // As per RFC 7118 Section 4.2: we need to support both binary and text frames
        if(frame instanceof TextWebSocketFrame) {
            content = ((TextWebSocketFrame)frame).content();
        } else if(frame instanceof BinaryWebSocketFrame) {
            content = ((BinaryWebSocketFrame)frame).content();
        } else {
            throw new Exception("Unexpected WebSocketFrame type: " + frame.getClass().getName());
        }
    
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            logger.logDebug("Decoding message: \n" + content.toString(io.netty.util.CharsetUtil.UTF_8));
        }
                                    
        do {
            if(nettyMessageParser.parseBytes(content).isParsingComplete()) {
                try {      
                    sipMessage = nettyMessageParser.consumeSIPMessage();
                    if (sipMessage != null) {
                        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                            logger.logDebug("following message parsed, passing it up the stack \n" + sipMessage.toString());
                        }         
                        InetSocketAddress remoteAddress = (InetSocketAddress)ctx.channel().remoteAddress();
                        sipMessage.setRemoteAddress(remoteAddress.getAddress());
                        sipMessage.setRemotePort(remoteAddress.getPort());
                        sipMessage.setPeerPacketSourceAddress(remoteAddress.getAddress());
                        sipMessage.setPeerPacketSourcePort(remoteAddress.getPort());                          
                        
                        MessageChannel nettyMessageChannel = nettyMessageProcessor.createMessageChannel(ctx.channel());                              

                        // RFC 7118 Section 6 Connection Keep-Alive : RFC5626 CRLF Keep Alive Support
                        if (sipMessage.isNullRequest()) {
                            NettyMessageHandler.processCRLFs(ctx, sipMessage, nettyMessageChannel);
                            return;
                        }

                        final String callId = sipMessage.getCallId().getCallId();
                        if (callId == null || callId.trim().length() < 1) {
                            // http://code.google.com/p/jain-sip/issues/detail?id=18
                            // NIO Message with no Call-ID throws NPE
                            logger.logError("Error occured processing message " + content.toString(Charset.forName("UTF-8")), 
                                new IOException("received message with no Call-ID"));                                
                            return;
                        }
                        
                        RawMessageChannel rawMessageChannel = (RawMessageChannel)nettyMessageChannel;
                        IncomingMessageProcessingTask incomingMessageProcessingTask = 
                            new IncomingMessageProcessingTask(rawMessageChannel, sipMessage);
                        sipStack.getMessageProcessorExecutor().addTaskLast(incomingMessageProcessingTask);   
                    }
                } catch (Exception e) {
                    e.printStackTrace();            
                    if(logger.isLoggingEnabled(LogWriter.TRACE_ERROR)) {   
                        logger.logError(
                            "Parsing issue !  " + content.toString(io.netty.util.CharsetUtil.UTF_8) + " " + e.getMessage(), e);
                    }
                }  
            }                 
        } while (sipMessage != null && content.readableBytes() > 0);
        if(sipMessage == null) {
            if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                logger.logDebug("No SIPMessage decoded ! ");
            }
        }    
    }
}
