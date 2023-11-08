package gov.nist.javax.sip.stack;

import java.util.List;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.NettyMessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class NettyStreamMessageDecoder extends ByteToMessageDecoder {
    private static StackLogger logger = CommonLogger.getLogger(NettyStreamMessageDecoder.class);

    NettyMessageParser nettyMessageParser = null;

    public NettyStreamMessageDecoder(SIPTransactionStack sipStack) {    
        this.nettyMessageParser = new NettyMessageParser(
            sipStack.getMessageParserFactory().createMessageParser(sipStack), 
            sipStack.getMaxMessageSize());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {            
        SIPMessage sipMessage = null;          
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            logger.logDebug("Decoding message: \n" + in.toString(io.netty.util.CharsetUtil.UTF_8));
        }
        try {            
            while (sipMessage == null && in.readableBytes() > 0) {
                nettyMessageParser.parseBytes(in);   
                if(nettyMessageParser.isParsingComplete()) {
                    sipMessage = nettyMessageParser.consumeSIPMessage();
                }                 
            }
            if (sipMessage != null) {
                if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                    logger.logDebug("following message parsed, passing it up the stack and resetting \n" + sipMessage.toString());
                }                                
                out.add(sipMessage);            
            }
        } catch (Exception e) {
            e.printStackTrace();            
            if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                logger.logDebug(
                    "Parsing issue !  " + in.toString(io.netty.util.CharsetUtil.UTF_8) + " " + e.getMessage(), e);
            }
        }                     
    }
}
