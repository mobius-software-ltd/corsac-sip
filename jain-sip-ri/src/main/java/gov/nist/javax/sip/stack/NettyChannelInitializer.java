package gov.nist.javax.sip.stack;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

/**
 *  special handler that is purposed to help a user configure a new Channel
 */
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private NettyTCPMessageProcessor nettyTCPMessageProcessor;

    public NettyChannelInitializer(NettyTCPMessageProcessor nettyTCPMessageProcessor) {
        this.nettyTCPMessageProcessor = nettyTCPMessageProcessor; 
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {        
        ChannelPipeline pipeline = ch.pipeline();
        
        // Decoders
        pipeline.addLast("bytesDecoder",
                        new ByteArrayDecoder());

        // Encoder
        pipeline.addLast("bytesEncoder", new ByteArrayEncoder());

        pipeline.addLast(new NettyMessageHandler(nettyTCPMessageProcessor));
    }
}
