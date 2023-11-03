package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ListeningPoint;

import gov.nist.core.CommonLogger;
import gov.nist.core.HostPort;
import gov.nist.core.StackLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;

public class NettyDatagramMessageProcessor extends MessageProcessor implements NettyMessageProcessor {
    private static StackLogger logger = CommonLogger.getLogger(NettyDatagramMessageProcessor.class);
    
    protected final Map<String, NettyDatagramMessageChannel> messageChannels;
    protected final List<Channel> serverChannels;
    
    // multithreaded event loop that handles incoming connection and I/O operations
    EpollEventLoopGroup epollEventLoopGroup;    
    
    /**
    * Constructor.
    *
    * @param sipStack pointer to the stack.
    */
    protected NettyDatagramMessageProcessor(InetAddress ipAddress,
            SIPTransactionStack sipStack, int port) throws IOException {
        super(ipAddress, port, ListeningPoint.UDP, sipStack);

        this.messageChannels = new ConcurrentHashMap<String, NettyDatagramMessageChannel>();
        epollEventLoopGroup = new EpollEventLoopGroup(sipStack.threadPoolSize);
        serverChannels = new ArrayList<>(sipStack.threadPoolSize);
    }
    
    @Override
    public SIPTransactionStack getSIPStack() {
        return sipStack;
    }
    
    @Override
    public MessageChannel createMessageChannel(Channel channel) {
        return new NettyDatagramMessageChannel(channel, this);
    }

    @Override
    public MessageChannel createMessageChannel(HostPort targetHostPort) throws IOException {
        return createMessageChannel(targetHostPort.getInetAddress(), targetHostPort.getPort());     
    }
    
    @Override
    public MessageChannel createMessageChannel(InetAddress targetHost, int port) throws IOException {
        return new NettyDatagramMessageChannel(
            serverChannels.get(port % serverChannels.size()),
            this, targetHost, port);
    }
    
    @Override
    public void start() throws IOException {
        
        Bootstrap connectionlessBootstrap = new Bootstrap();
        connectionlessBootstrap.option(ChannelOption.SO_SNDBUF, sipStack.getSendUdpBufferSize());
        connectionlessBootstrap.option(ChannelOption.SO_RCVBUF, sipStack.getReceiveUdpBufferSize());
        // The SO_REUSEPORT option is implemented by the kernel and will dispatch received datagrams
        // to the created sockets based on the source transport address. 
        // Therefore, datagrams received from the same transport address will be dispatched
        // to the same socket and therefore will be processed serially by the same Channel.
        connectionlessBootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
        connectionlessBootstrap.option(EpollChannelOption.IP_RECVORIGDSTADDR, true);
        connectionlessBootstrap.option(EpollChannelOption.IP_FREEBIND, true);
        connectionlessBootstrap.handler(new NettyDatagramChannelInitializer(this));
        connectionlessBootstrap.channel(EpollDatagramChannel.class);        
        connectionlessBootstrap.group(epollEventLoopGroup);
        
        ChannelFuture future;
        // start all our messageChannels (unless the thread pool size is
        // infinity.
        if (sipStack.threadPoolSize != -1) {
            for(int i = 0; i < sipStack.threadPoolSize; ++i) {
                if(super.getIpAddress() == null) {
                    future = connectionlessBootstrap.bind(new InetSocketAddress("0.0.0.0", port));
                } else {
                    future = connectionlessBootstrap.bind(new InetSocketAddress(super.getIpAddress(), port));
                }
                
                future.awaitUninterruptibly();
                if(!future.isSuccess()) {
                    logger.logError("Channel Not Connected:" + future.cause());
                }
                
                serverChannels.add(future.channel());                           
            } 
        }
    }
        
    @Override
    public void stop() {
        for (final Channel channel : serverChannels) {
            try {
                channel.closeFuture().await(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        serverChannels.clear();
        epollEventLoopGroup.shutdownGracefully();
    }
    
    /**
    * Default target port for UDP
    */
    public int getDefaultTargetPort() {
        return 5060;
    }
    
    /**
    * UDP is not a secure protocol.
    */
    public boolean isSecure() {
        return false;
    }
    
    /**
    * UDP can handle a message as large as the MAX_DATAGRAM_SIZE.
    */
    public int getMaximumMessageSize() {
        return sipStack.getReceiveUdpBufferSize();
    }
    
    /**
    * Return true if there are any messages in use.
    */
    public boolean inUse() {
        return !serverChannels.isEmpty();
    }   
}
    