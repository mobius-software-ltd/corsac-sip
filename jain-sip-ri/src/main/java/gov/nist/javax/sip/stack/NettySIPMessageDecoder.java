package gov.nist.javax.sip.stack;

import java.text.ParseException;
import java.util.List;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.MessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class NettySIPMessageDecoder extends ByteToMessageDecoder {
    private static StackLogger logger = CommonLogger.getLogger(NettySIPMessageDecoder.class);

    private MessageParser smp = null;

    public NettySIPMessageDecoder(SIPTransactionStack sipStack) {    
        this.smp = sipStack.getMessageParserFactory().createMessageParser(sipStack);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) { 
        logger.logError("readable bytes: \n" + in.readableBytes());   
              
        byte[] msg = new byte[in.readableBytes()];
        in.readBytes(msg);

        String message = new String(msg);
        logger.logError("received following message: \n" + message);         

        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("received following message: \n" + new String(message));
        }                
        				
        try {            
            SIPMessage sipMessage = smp.parseSIPMessage(msg, true, false, null);            
            if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                logger.logDebug(sipMessage.toString());
            }  
            out.add(sipMessage);
        } catch (ParseException e) {
            e.printStackTrace();
            logger.logDebug(
                    "Parsing issue !  " + new String(msg.toString()) + " " + e.getMessage());
        }              
    }
}
