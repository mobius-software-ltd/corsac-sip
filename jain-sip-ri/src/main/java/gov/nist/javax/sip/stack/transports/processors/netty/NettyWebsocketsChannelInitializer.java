package gov.nist.javax.sip.stack.transports.processors.netty;

import gov.nist.javax.sip.stack.SIPTransactionStack;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;

public class NettyWebsocketsChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final String WEBSOCKET_PATH = "/websocket";
    private NettyMessageProcessor nettyMessageProcessor;
    private SIPTransactionStack sipStack;
    private final SslContext sslCtx;

    public NettyWebsocketsChannelInitializer(
            NettyMessageProcessor nettyMessageProcessor, 
            SslContext sslCtx) {
        this.nettyMessageProcessor = nettyMessageProcessor; 
        sipStack = nettyMessageProcessor.getSIPStack();
        this.sslCtx = sslCtx;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        // pipeline.addLast(new WebSocketIndexPageHandler(WEBSOCKET_PATH));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true));
        // pipeline.addLast(new WebSocketFrameHandler());
    }
}
