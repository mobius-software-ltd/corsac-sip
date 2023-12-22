package gov.nist.javax.sip.stack.transports.processors.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
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
						.build();
        pipeline.addLast(new WebSocket13FrameDecoder(decoderConfig));
        // pipeline.addLast(new WebSocket13FrameEncoder(false));
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        // pipeline.addLast(new WebSocketServerCompressionHandler());
        // pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath, SUBPROTOCOL, true));
        pipeline.addLast(new WebSocketFrameHandler(nettyMessageProcessor));        
    }
}
