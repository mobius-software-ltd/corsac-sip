package gov.nist.javax.sip.stack;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.ssl.SslContext;

/**
 *  special handler that is purposed to help a user configure a new Channel
 */
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private NettyStreamMessageProcessor nettyStreamMessageProcessor;
    private final SslContext sslCtx;

    public NettyChannelInitializer(NettyStreamMessageProcessor nettyStreamMessageProcessor, SslContext sslCtx) {
        this.nettyStreamMessageProcessor = nettyStreamMessageProcessor; 
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
        if(sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }

        // Decoders
        pipeline.addLast("NettySIPMessageDecoder",
                        new NettySIPMessageDecoder(nettyStreamMessageProcessor.sipStack));

        // Encoder
        pipeline.addLast("bytesEncoder", new ByteArrayEncoder());

        pipeline.addLast(new NettyMessageHandler(nettyStreamMessageProcessor));
    }
}
