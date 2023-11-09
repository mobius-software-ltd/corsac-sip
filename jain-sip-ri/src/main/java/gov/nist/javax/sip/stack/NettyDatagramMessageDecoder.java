package gov.nist.javax.sip.stack;

import java.net.InetSocketAddress;
import java.util.List;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.MessageParser;
import gov.nist.javax.sip.parser.NettyMessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

public class NettyDatagramMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
    private static StackLogger logger = CommonLogger.getLogger(NettyDatagramMessageDecoder.class);

    private NettyMessageProcessor nettyMessageProcessor;
    private MessageParser sipMessageParser = null;


    public NettyDatagramMessageDecoder(NettyMessageProcessor nettyMessageProcessor) {            
        this.nettyMessageProcessor = nettyMessageProcessor;
        sipMessageParser = nettyMessageProcessor.getSIPStack().getMessageParserFactory().createMessageParser(nettyMessageProcessor.getSIPStack());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {            
        NettyMessageParser nettyMessageParser = new NettyMessageParser(
                sipMessageParser, 
                nettyMessageProcessor.getSIPStack().getMaxMessageSize());
        SIPMessage sipMessage = null;  
        ByteBuf content =  msg.content();
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            logger.logDebug("Decoding message: \n" + content.toString(io.netty.util.CharsetUtil.UTF_8));
        }
        try {                                  
            while (sipMessage == null && content.readableBytes() > 0) {
                if(nettyMessageParser.parseBytes(content).isParsingComplete()) {
                    sipMessage = nettyMessageParser.consumeSIPMessage();
                }                
            }
            // SIPMessage sipMessage = null;
            // int readableBytes = content.readableBytes();
            // // Dealing with CRLF sent by some SIP Clients
            // if(readableBytes == 2 || readableBytes == 4) {
            //     sipMessage = new SIPRequest();
			// 	sipMessage.setSize(readableBytes);
			// 	sipMessage.setNullRequest();
            // } else {
            //     sipMessage = sipMessageParser.parseSIPMessage(
            //         content.readBytes(content.readableBytes()).array(), 
            //         false, 
            //         false, 
            //         null);
            // }
            if (sipMessage != null) {
                if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                    logger.logDebug("following message parsed, passing it up the stack and resetting \n" + sipMessage.toString());
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
}
