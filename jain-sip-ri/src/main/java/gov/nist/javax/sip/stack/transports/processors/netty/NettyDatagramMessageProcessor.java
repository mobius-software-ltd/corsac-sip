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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import javax.sip.ListeningPoint;

import gov.nist.core.CommonLogger;
import gov.nist.core.HostPort;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;

/**
 * Netty Based Datagram Message Processor to handle creation of 
 * UDP Connection less Server and Datagram Message Channels
 * 
 * @author Jean Deruelle
 */
public class NettyDatagramMessageProcessor extends MessageProcessor implements NettyMessageProcessor {
    private static StackLogger logger = CommonLogger.getLogger(NettyDatagramMessageProcessor.class);
    
    protected final List<Channel> serverChannels;
    
    // multithreaded event loop that handles incoming connection and I/O operations
    EventLoopGroup eventLoopGroup;    
    
    /**
    * Constructor.
    *
    * @param sipStack pointer to the stack.
    */
    protected NettyDatagramMessageProcessor(InetAddress ipAddress,
            SIPTransactionStack sipStack, int port) throws IOException {
        super(ipAddress, port, ListeningPoint.UDP, sipStack);

        int threadPoolSize = sipStack.getThreadPoolSize();
        eventLoopGroup = newNioOrEpollEventLoopGroup(threadPoolSize);
        serverChannels = new ArrayList<>(threadPoolSize);
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
        connectionlessBootstrap.channel(nioOrEpollServerDatagramChannel());
        connectionlessBootstrap.group(eventLoopGroup);
        
        ChannelFuture future;
        int threadPoolSize = sipStack.getThreadPoolSize();
        // start all our messageChannels (unless the thread pool size is
        // infinity.
        if (threadPoolSize != -1) {
            for(int i = 0; i < threadPoolSize; ++i) {
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
        eventLoopGroup.shutdownGracefully();
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
    
    public Class<? extends DatagramChannel> nioOrEpollServerDatagramChannel() {
        if (Epoll.isAvailable()) {
            return EpollDatagramChannel.class;
        } else {
            logger.logWarning("EPoll is not enabled or supported on this platform, using NIO.");
            return DatagramChannel.class;
        }
    }

    public EventLoopGroup newNioOrEpollEventLoopGroup(int threads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(threads);
        } else {
            return new NioEventLoopGroup(threads);
        }
    }
}
    