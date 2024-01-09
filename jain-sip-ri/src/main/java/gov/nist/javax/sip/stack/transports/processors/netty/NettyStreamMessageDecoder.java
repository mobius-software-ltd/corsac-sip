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
package gov.nist.javax.sip.stack.transports.processors.netty;

import java.util.List;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.NettyMessageParser;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * Netty Based Stream Based Transport Protocol (TCP, TLS, ...) Message Decoder to handle Incoming SIP Messages
 * 
 * @author Jean Deruelle
 */
public class NettyStreamMessageDecoder extends ByteToMessageDecoder {
    private static StackLogger logger = CommonLogger.getLogger(NettyStreamMessageDecoder.class);

    NettyMessageParser nettyMessageParser = null;

    public NettyStreamMessageDecoder(SIPTransactionStack sipStack) {    
        this.nettyMessageParser = new NettyMessageParser(            
            sipStack.getMaxMessageSize(),
            sipStack.isComputeContentLengthFromMessage());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {            
        SIPMessage sipMessage = null;          
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            logger.logDebug("Decoding message: \n" + in.toString(io.netty.util.CharsetUtil.UTF_8));
        }
                  
        do {
        	sipMessage=null;
            if(nettyMessageParser.parseBytes(in).isParsingComplete()) {
                try {  
                    sipMessage = nettyMessageParser.consumeSIPMessage();
                    if (sipMessage != null) {
                        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                            logger.logDebug("following message parsed, passing it up the stack \n" + sipMessage.toString());
                        }         
                        out.add(sipMessage);            
                    }
                } catch (Exception e) {                    
                    if(logger.isLoggingEnabled(LogWriter.TRACE_ERROR)) {   
                        logger.logError(
                            "Parsing issue !  " + in.toString(io.netty.util.CharsetUtil.UTF_8) + " " + e.getMessage(), e);
                    }
                    // in.clear();
                }    
            }
        } while (sipMessage != null && in.readableBytes() > 0);                    
        if(sipMessage == null) {
            if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                logger.logDebug("No SIPMessage decoded ! ");
            }
        }        
    }
}
