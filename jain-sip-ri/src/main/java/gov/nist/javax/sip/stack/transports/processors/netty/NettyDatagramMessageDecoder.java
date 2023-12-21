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

import java.net.InetSocketAddress;
import java.util.List;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.NettyMessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

/**
 * Netty Based Datagram Message Decoder to handle Incoming SIP Messages
 * 
 * @author Jean Deruelle
 */
public class NettyDatagramMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
    private static StackLogger logger = CommonLogger.getLogger(NettyDatagramMessageDecoder.class);

    private NettyMessageProcessor nettyMessageProcessor;    

    public NettyDatagramMessageDecoder(NettyMessageProcessor nettyMessageProcessor) {            
        this.nettyMessageProcessor = nettyMessageProcessor;        
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {            
        NettyMessageParser nettyMessageParser = new NettyMessageParser(                
                nettyMessageProcessor.getSIPStack().getMaxMessageSize(),
                nettyMessageProcessor.getSIPStack().isComputeContentLengthFromMessage());
        SIPMessage sipMessage = null;  
        ByteBuf content =  msg.content();
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
                        InetSocketAddress remoteAddress = (InetSocketAddress)msg.sender();
                        sipMessage.setRemoteAddress(remoteAddress.getAddress());
                        sipMessage.setRemotePort(remoteAddress.getPort());
                        sipMessage.setPeerPacketSourceAddress(remoteAddress.getAddress());
                        sipMessage.setPeerPacketSourcePort(remoteAddress.getPort());                          
                        
                        out.add(sipMessage);                                 
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
