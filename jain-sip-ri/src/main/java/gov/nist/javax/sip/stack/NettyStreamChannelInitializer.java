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
package gov.nist.javax.sip.stack;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 *  Netty Stream Based Transport Protocol Initializer for SIP Message
 * 
 * @author Jean Deruelle
 */
public class NettyStreamChannelInitializer extends ChannelInitializer<SocketChannel> {

    private NettyMessageProcessor nettyMessageProcessor;
    private SIPTransactionStack sipStack;
    private final SslContext sslCtx;

    public NettyStreamChannelInitializer(
            NettyMessageProcessor nettyMessageProcessor, 
            SslContext sslCtx) {
        this.nettyMessageProcessor = nettyMessageProcessor; 
        sipStack = nettyMessageProcessor.getSIPStack();
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {        
        ChannelPipeline pipeline = ch.pipeline();

        // Add SSL handler first to encrypt and decrypt everything.
        // In this example, we use a bogus certificate in the server side
        // and accept any invalid certificates in the client side.
        // You will need something more complicated to identify both
        // and server in the real world.
        if (sslCtx != null) {
            SslHandler sslHandler = sslCtx.newHandler(ch.alloc());            
            if(sslCtx.isClient()) {
                sslHandler.engine().setUseClientMode(true);
            }            
            pipeline.addLast(sslHandler);
            
        }
        
        // Add support for socket timeout
        if (sipStack.nioSocketMaxIdleTime > 0) {
            pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler((int) sipStack.nioSocketMaxIdleTime / 1000));
        }
        // Decoders
        pipeline.addLast("NettySIPMessageDecoder",
                        new NettyStreamMessageDecoder(sipStack));

        // Encoder
        pipeline.addLast("bytesEncoder", new ByteArrayEncoder());

        pipeline.addLast("NettyMessageHandler", new NettyMessageHandler(nettyMessageProcessor));
    }
}
