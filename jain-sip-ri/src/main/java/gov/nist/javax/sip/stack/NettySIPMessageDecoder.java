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

public class NettySIPMessageDecoder extends ByteToMessageDecoder {
    private static StackLogger logger = CommonLogger.getLogger(NettySIPMessageDecoder.class);

    NettyMessageParser nettyMessageParser = null;

    public NettySIPMessageDecoder(SIPTransactionStack sipStack) {    
        this.nettyMessageParser = new NettyMessageParser(sipStack.getMessageParserFactory().createMessageParser(sipStack));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {            
        try {
            SIPMessage sipMessage = nettyMessageParser.getSIPMessage();            
            while (sipMessage == null && in.readableBytes() > 0) {
                sipMessage = nettyMessageParser.addBytes(in);                    
            }
            if (sipMessage != null) {
                if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                    logger.logDebug("following message parsed, passing it up the stack and resetting \n" + sipMessage.toString());
                }                
                nettyMessageParser.reset();
                out.add(sipMessage);            
            }
        } catch (Exception e) {
            e.printStackTrace();            
            if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                logger.logDebug(
                    "Parsing issue !  " + new String(nettyMessageParser.getMessage().toString()) + " " + e.getMessage());
            }
        }                     
    }
}
