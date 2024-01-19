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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

public class NettyWebSocketFrameDecoder extends WebSocket13FrameDecoder {
    private static StackLogger logger = CommonLogger.getLogger(NettyWebSocketFrameDecoder.class);
    NettyMessageParser nettyMessageParser;

    public NettyWebSocketFrameDecoder(NettyStreamMessageProcessor nettyMessageProcessor, boolean expectMaskedFrames,
            boolean allowExtensions, int maxFramePayloadLength) {
        super(expectMaskedFrames, allowExtensions, maxFramePayloadLength);
        nettyMessageParser = new NettyMessageParser(
                nettyMessageProcessor.getSIPStack().getMaxMessageSize(),
                nettyMessageProcessor.getSIPStack().isComputeContentLengthFromMessage());
    }

    public NettyWebSocketFrameDecoder(NettyStreamMessageProcessor nettyMessageProcessor, boolean expectMaskedFrames,
            boolean allowExtensions, int maxFramePayloadLength,
            boolean allowMaskMismatch) {
        super(expectMaskedFrames, allowExtensions, maxFramePayloadLength, allowMaskMismatch);
        nettyMessageParser = new NettyMessageParser(
                nettyMessageProcessor.getSIPStack().getMaxMessageSize(),
                nettyMessageProcessor.getSIPStack().isComputeContentLengthFromMessage());
    }

    public NettyWebSocketFrameDecoder(NettyStreamMessageProcessor nettyMessageProcessor,
            WebSocketDecoderConfig decoderConfig) {
        super(decoderConfig);
        nettyMessageParser = new NettyMessageParser(
                nettyMessageProcessor.getSIPStack().getMaxMessageSize(),
                nettyMessageProcessor.getSIPStack().isComputeContentLengthFromMessage());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        super.decode(ctx, in, out);
        if(out.size() > 0) {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("decode: " + out.get(0));
            }
        } else {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("decode: no message decoded");
            }
            return;
        }
        WebSocketFrame frame = (WebSocketFrame) out.remove(0);
        ByteBuf content = null;
        if (frame instanceof TextWebSocketFrame) {
            content = ((TextWebSocketFrame) frame).content();
        } else if (frame instanceof BinaryWebSocketFrame) {
            content = ((BinaryWebSocketFrame) frame).content();
        } else {
            throw new Exception("Unexpected WebSocketFrame type: " + frame.getClass().getName());
        }

        SIPMessage sipMessage = null;
        do {
            if (nettyMessageParser.parseBytes(content).isParsingComplete()) {
                try {
                    sipMessage = nettyMessageParser.consumeSIPMessage();
                    if (sipMessage != null) {
                        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                            logger.logDebug(
                                    "following message parsed, passing it up the stack \n" + sipMessage.toString());
                        }
                        out.add(sipMessage);
                    }
                } catch (Exception e) {
                    // e.printStackTrace();
                    if (logger.isLoggingEnabled(LogWriter.TRACE_ERROR)) {
                        logger.logError(
                                "Parsing issue !  " + in.toString(io.netty.util.CharsetUtil.UTF_8) + " "
                                        + e.getMessage(),
                                e);
                    }
                }
            }
        } while (sipMessage != null && in.readableBytes() > 0);
        if (sipMessage == null) {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("No SIPMessage decoded ! ");
            }
        } else {
            if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
                logger.logDebug("done reading ! ");
            }
        }  
    }
}
