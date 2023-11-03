package gov.nist.javax.sip.stack;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

/**
 *  special handler that is purposed to help a user configure a new Channel
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
