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
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

/**
 *  Netty Datagram Initializer for SIP Message
 * 
 * @author Jean Deruelle
 */
public class NettyDatagramChannelInitializer extends ChannelInitializer<DatagramChannel> {

    private NettyMessageProcessor nettyMessageProcessor;    

    public NettyDatagramChannelInitializer(
            NettyMessageProcessor nettyMessageProcessor) {
        this.nettyMessageProcessor = nettyMessageProcessor;                
    }

    @Override
    public void initChannel(DatagramChannel ch) throws Exception {        
        ChannelPipeline pipeline = ch.pipeline();        
                
        // Decoders
        pipeline.addLast("decoder",
                        new NettyDatagramMessageDecoder(nettyMessageProcessor));

        // Encoder
        pipeline.addLast("bytesEncoder", new ByteArrayEncoder());

        pipeline.addLast("handler", new NettyMessageHandler(nettyMessageProcessor));
    }
}
