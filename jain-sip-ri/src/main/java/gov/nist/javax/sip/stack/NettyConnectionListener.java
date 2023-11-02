package gov.nist.javax.sip.stack;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sip.ListeningPoint;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipStackImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.ssl.SslHandler;


public class NettyConnectionListener implements ChannelFutureListener {
    private static StackLogger logger = CommonLogger
			.getLogger(NettyConnectionListener.class);
    private NettyStreamMessageChannel messageChannel;
    private SipStackImpl sipStack;
    private ConcurrentLinkedQueue<ByteBuf> pendingMessages;    

    public NettyConnectionListener(NettyStreamMessageChannel nettyStreamMessageChannel) {
        this.messageChannel = nettyStreamMessageChannel;
        this.sipStack = (SipStackImpl) messageChannel.sipStack;
        pendingMessages = new ConcurrentLinkedQueue<ByteBuf>();        
    }

    public void addPendingMessage(ByteBuf byteBuf) {
        pendingMessages.add(byteBuf);
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (!channelFuture.isSuccess()) {																 
            if(sipStack.getSelfRoutingThreadpoolExecutor() != null) {
                sipStack.getSelfRoutingThreadpoolExecutor().execute(
                    new NettyConnectionFailureThread(messageChannel) 
                );
            } else {
                messageChannel.triggerConnectFailure();                                           
            }
            return;
        } else {
            Channel channel = ((ChannelFuture)channelFuture).channel();
            messageChannel.channel = channel;
            messageChannel.peerAddress = ((InetSocketAddress) channel.remoteAddress()).getAddress();
            messageChannel.peerPort = ((InetSocketAddress) channel.remoteAddress()).getPort();						
            
            if(messageChannel.getTransport().equalsIgnoreCase(ListeningPoint.TLS)) {
                SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug("SSL Handler " + sslHandler);
                }
                if (sslHandler != null) {
                    sslHandler.handshakeFuture().addListener(new io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<io.netty.channel.Channel>>() {
                        @Override
                        public void operationComplete(io.netty.util.concurrent.Future<io.netty.channel.Channel> future) throws Exception {
                            messageChannel.handshakeCompletedListener.setPeerCertificates(
                                sslHandler.engine().getSession().getPeerCertificates());
                            messageChannel.handshakeCompletedListener.setLocalCertificates(
                                sslHandler.engine().getSession().getLocalCertificates());
                            messageChannel.handshakeCompletedListener.setCipherSuite(
                                sslHandler.engine().getSession().getCipherSuite());
                            if (future.isSuccess()) {
                                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                                    logger.logDebug("SSL handshake completed successfully");
                                }
                                messageChannel.handshakeCompleted = true;									
                            } else {
                                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                                    logger.logDebug("SSL handshake failed " + future.cause() + ", closing the channel");
                                }
                                messageChannel.handshakeCompleted = false;									
                                channel.close().await();
                            }
                        }
                    });
                }
            }
            // writing all pending messages
            while (!pendingMessages.isEmpty()) {
                ByteBuf byteBuf = pendingMessages.remove();            
                messageChannel.writeMessage(byteBuf);
            }                									
        }            
    }    
}
