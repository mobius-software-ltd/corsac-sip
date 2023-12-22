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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.ssl.SslContext;

public class NettyWebsocketsChannelInitializer extends ChannelInitializer<SocketChannel> {
    // private static final String SUBPROTOCOL = null;
    
    private NettyStreamMessageProcessor nettyMessageProcessor;
    private final SslContext sslCtx;
    // private final String websocketPath;

    public NettyWebsocketsChannelInitializer(
            NettyStreamMessageProcessor nettyMessageProcessor, 
            SslContext sslCtx) {
        this.nettyMessageProcessor = nettyMessageProcessor; 
        // websocketPath = "/websocket";
        this.sslCtx = sslCtx;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }        
        WebSocketDecoderConfig decoderConfig =
					WebSocketDecoderConfig.newBuilder()
						.expectMaskedFrames(false)
						.allowMaskMismatch(true)
						.allowExtensions(true)
                        .maxFramePayloadLength(1048576)
						.build();
        // pipeline.addLast(new WebSocket13FrameDecoder(decoderConfig));
        pipeline.addLast(new NettyWebSocketFrameDecoder(nettyMessageProcessor, decoderConfig));
        pipeline.addLast(new WebSocket13FrameEncoder(false));
        // pipeline.addLast(new HttpServerCodec());
        // pipeline.addLast(new HttpObjectAggregator(65536));
        // pipeline.addLast(new WebSocketServerCompressionHandler());
        // pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath, SUBPROTOCOL, true));
        pipeline.addLast(new NettyMessageHandler(nettyMessageProcessor));        
    }
}
