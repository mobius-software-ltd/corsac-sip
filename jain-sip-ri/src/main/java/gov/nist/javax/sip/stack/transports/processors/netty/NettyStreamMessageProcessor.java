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
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.sip.ListeningPoint;

import gov.nist.core.CommonLogger;
import gov.nist.core.Host;
import gov.nist.core.HostPort;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.net.SecurityManagerProvider;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.ClientAuthType;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * Netty Based Datagram Message Processor to handle creation of 
 * UDP Connection less Server and Datagram Message Channels
 * 
 * @author Jean Deruelle
 */
public class NettyStreamMessageProcessor extends MessageProcessor implements NettyMessageProcessor {
	
	private static StackLogger logger = CommonLogger.getLogger(NettyStreamMessageProcessor.class);

    protected final Map<String, NettyStreamMessageChannel> messageChannels;
    
    // multithreaded event loop that handles incoming connection and I/O operations    
    EventLoopGroup bossGroup; 
    // multithreaded event loop that handles I/O operation and handles the traffic 
    // of the accepted connection once the boss accepts the connection
    // and registers the accepted connection to the worker
    EventLoopGroup workerGroup;

    NettyMessageHandler context;
    Channel channel;

    SslContext sslServerContext;
    SslContext sslClientContext;

    /**
     * Constructor.
     *
     * @param sipStack pointer to the stack.
     */
    protected NettyStreamMessageProcessor(InetAddress ipAddress,
            SIPTransactionStack sipStack, int port, String transport) throws IOException {

        super(ipAddress, port, transport, sipStack);                
        this.messageChannels = new ConcurrentHashMap <String, NettyStreamMessageChannel>();                
        this.bossGroup = new NioEventLoopGroup(1); 
        this.workerGroup = new NioEventLoopGroup(sipStack.getThreadPoolSize());
        if(transport.equals(ListeningPoint.TLS)) {
            SecurityManagerProvider securityManagerProvider = sipStack.getSecurityManagerProvider();
            if(sipStack.getClientAuth() == ClientAuthType.DisabledAll) {
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug(
                            "ClientAuth " + sipStack.getClientAuth()  +  " bypassing all cert validations");
                }                        
                this.sslServerContext = SslContextBuilder.forServer(securityManagerProvider.getKeyManagers(false)[0]).trustManager(trustAllCerts[0]).build();
                this.sslClientContext = SslContextBuilder.forClient().keyManager(securityManagerProvider.getKeyManagers(true)[0]).trustManager(trustAllCerts[0]).build();                        
            } else {
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug(
                            "ClientAuth " + sipStack.getClientAuth());
                }
                this.sslServerContext = SslContextBuilder.forServer(securityManagerProvider.getKeyManagers(false)[0]).trustManager(securityManagerProvider.getTrustManagers(false)[0]).build();
                this.sslClientContext = SslContextBuilder.forClient().keyManager(securityManagerProvider.getKeyManagers(true)[0]).trustManager(securityManagerProvider.getTrustManagers(true)[0]).build();                                                
            }
        }
    }

    /**
     * This private version is thread safe using proper critical session.
     * 
     * We don't use putIfAbset from CHM since creating a channel instance itself
     * is quite heavy. See https://github.com/RestComm/jain-sip/issues/80.
     *      
     * @param key
     * @param targetHost
     * @param port
     * @return
     * @throws IOException 
     * @throws InterruptedException
     */
    private MessageChannel createMessageChannel(String key, InetAddress targetHost, int port)  throws IOException {
        NettyStreamMessageChannel retval = messageChannels.get(key);        
        //once locked, we need to check condition again
        if( retval == null ) {
                retval = constructMessageChannel(targetHost,
                                port);
                this.messageChannels.put(key, retval);
                // retval.isCached = true;
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("key " + key);
                        logger.logDebug("Creating " + retval);
                }                
        }  		
        return retval;      
    }   

    @Override
    public MessageChannel createMessageChannel(HostPort targetHostPort) throws IOException {
    	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    		logger.logDebug("NettyStreamMessageProcessor::createMessageChannel: " + targetHostPort);
    	}
        MessageChannel retval = null;
    	try {
    		String key = MessageChannel.getKey(targetHostPort, transport);
		    retval = messageChannels.get(key);
                //here we use double-checked locking trying to reduce contention	
    		if (retval == null) {
                    retval = createMessageChannel(key, 
                            targetHostPort.getInetAddress(), targetHostPort.getPort());  			
		}    		
    	} finally {
    		if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    			logger.logDebug("NettyStreamMessageProcessor::createMessageChannel - exit " + retval);
    		}
    	}
        return retval;
    }
    
    public MessageChannel createMessageChannel(Channel channel) {
        
        InetSocketAddress socketAddress = ((InetSocketAddress)channel.remoteAddress());
    	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    		logger.logDebug("NettyStreamMessageProcessor::createMessageChannel: " + socketAddress.getAddress().getHostAddress()+":"+socketAddress.getPort());
    	}
        NettyStreamMessageChannel retval = null;
    	try {
            HostPort targetHostPort  = new HostPort();
            targetHostPort.setHost(new Host(socketAddress.getAddress().getHostAddress()));  
            targetHostPort.setPort(socketAddress.getPort());
    		String key = MessageChannel.getKey(targetHostPort, transport);
            retval = messageChannels.get(key);        
            //once locked, we need to check condition again
            if( retval == null ) {
                    retval = new NettyStreamMessageChannel(this, channel);        
                    this.messageChannels.put(key, retval);
                    // retval.isCached = true;
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                            logger.logDebug("key " + key);
                            logger.logDebug("Creating " + retval);
                    }                
            }  						   		
    	} finally {
    		if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    			logger.logDebug("MessageChannel::createMessageChannel - exit " + retval);
    		}
    	}
        return retval;
    }


    @Override
    public MessageChannel createMessageChannel(InetAddress targetHost, int port) throws IOException {
        String key = MessageChannel.getKey(targetHost, port, transport);
        MessageChannel retval = messageChannels.get(key);
        // MessageChannel retval = null;
            //here we use double-checked locking trying to reduce contention	
        if (retval == null) {
                retval = createMessageChannel(key, targetHost, port);
        }
	return retval;
    }

    NettyStreamMessageChannel constructMessageChannel (InetAddress targetHost, int port) throws IOException {
        return new NettyStreamMessageChannel(targetHost, port, sipStack, this);        
    }

    @Override
    public void start() throws IOException {
        try {
            ServerBootstrap server = new ServerBootstrap(); ; 
            server = server.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) 
             .handler(new LoggingHandler(LogLevel.DEBUG));
            if(transport.equals(ListeningPoint.TLS) || transport.equals(ListeningPoint.TCP)) {
                server = server.childHandler(new NettyStreamChannelInitializer(this, sslServerContext));
            } else if(transport.equals(ListeningPointExt.WS) || transport.equals(ListeningPointExt.WSS)) {
                server = server.childHandler(new NettyWebsocketsChannelInitializer(this, sslServerContext));
            } else if(transport.equals(ListeningPoint.SCTP)) {
                server = server.childHandler(new NettySctpChannelInitializer(this));
                server.childOption(SctpChannelOption.SCTP_NODELAY, sipStack.getSctpNodelay());
                server.childOption(SctpChannelOption.SCTP_DISABLE_FRAGMENTS, sipStack.getSctpDisableFragments());
                server.childOption(SctpChannelOption.SCTP_FRAGMENT_INTERLEAVE, sipStack.getSctpFragmentInterleave());
                // server.childOption(SctpChannelOption.SCTP_INIT_MAXSTREAMS, sipStack.getSctpInitMaxStreams());
                server.childOption(SctpChannelOption.SO_SNDBUF, sipStack.getSctpSoSndbuf());
                server.childOption(SctpChannelOption.SO_RCVBUF, sipStack.getSctpSoRcvbuf());
                server.childOption(SctpChannelOption.SO_LINGER, sipStack.getSctpSoLinger());
            }
             // TODO Add Option based on sip stack config
             server.option(ChannelOption.SO_BACKLOG, 128) // for the NioServerSocketChannel that accepts incoming connections.
             .childOption(ChannelOption.SO_KEEPALIVE, true); // for the Channels accepted by the parent ServerChannel, which is NioSocketChannel in this case
            
            // Bind and start to accept incoming connections.
            channel = server.bind(port).await().channel();                   
            
            // Create a new thread to run the server
            new Thread(() -> {
                try {
                    // Wait until the server socket is closed.
                    // In this example, this does not happen, but you can do that to gracefully
                    // shut down your server.                    
                    channel.closeFuture().sync();
                } catch (InterruptedException e) {
                    logger.logException(e);
                } finally {
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                }
            }).start();
        } catch (InterruptedException e) {
            logger.logException(e);
        }
    }

    @Override
    public void stop() {
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

    @Override
    public boolean inUse() {
        return !workerGroup.isTerminated();
    }

    /**
     * Get port on which to listen for incoming stuff.
     *
     * @return port on which I am listening.
     */
    public int getPort() {
        return port;
    }

    /**
     * Default target port for TCP
     */
    public int getDefaultTargetPort() {
        return 5060;
    }

    /**
     * TCP is not a secure protocol.
     */
    public boolean isSecure() {
        return false;
    }    
    
    /**
     * TCP can handle an unlimited number of bytes.
     */
    public int getMaximumMessageSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public SIPTransactionStack getSIPStack() {
        return this.sipStack;
    }

    public void close() {
        // closing the channels
        for (Object messageChannel : messageChannels.values()) {
			((MessageChannel)messageChannel).close();
          }
        // channel.close();
    }

    protected void remove(NettyStreamMessageChannel messageChannel) {

        String key = messageChannel.getKey();
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug(Thread.currentThread() + " removing " + key + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
        }

        /** May have been removed already */
        if (messageChannels.get(key) == messageChannel)
            this.messageChannels.remove(key);
        
        // if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
        //     logger.logDebug(Thread.currentThread() + " Removing incoming channel " + key + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
        // incomingMessageChannels.remove(key);
    }
	
    protected void cacheMessageChannel(NettyStreamMessageChannel messageChannel) {
        String key = messageChannel.getKey();
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("Caching " + key + " value " + messageChannel);
        // NettyStreamMessageChannel previousChannel = 
            messageChannels.putIfAbsent(key, messageChannel);
        // FIXME: should we close the channel here ? This is making the testsuite fail with Netty
        // if (previousChannel != null) {
            // if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            //     logger.logDebug("Closing " + key);
            // previousChannel.close();
        // }                
    }
    
    public boolean closeReliableConnection(String peerAddress, int peerPort) throws IllegalArgumentException {

        validatePortInRange(peerPort);

        HostPort hostPort = new HostPort();
        hostPort.setHost(new Host(peerAddress));
        hostPort.setPort(peerPort);

        String messageChannelKey = MessageChannel.getKey(hostPort, "TCP");

        NettyStreamMessageChannel foundMessageChannel = messageChannels.remove(messageChannelKey);

        if (foundMessageChannel != null) {
            foundMessageChannel.close();
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug(Thread.currentThread() + " Removing channel " + messageChannelKey + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
            // incomingMessageChannels.remove(messageChannelKey);
            return true;
        }
        
        // foundMessageChannel = incomingMessageChannels.get(messageChannelKey);

        // if (foundMessageChannel != null) {
        //     foundMessageChannel.close();
        //     if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
        //         logger.logDebug(Thread.currentThread() + " Removing incoming channel " + messageChannelKey + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
        //     incomingMessageChannels.remove(messageChannelKey);
        //     messageChannels.remove(messageChannelKey);
        //     return true;
        // }
        
        return false;
    }
    
    public boolean setKeepAliveTimeout(String peerAddress, int peerPort, long keepAliveTimeout) {

        validatePortInRange(peerPort);

        HostPort hostPort  = new HostPort();
        hostPort.setHost(new Host(peerAddress));
        hostPort.setPort(peerPort);

        String messageChannelKey = MessageChannel.getKey(hostPort, "TCP");
                
        NettyStreamMessageChannel foundMessageChannel = messageChannels.get(messageChannelKey);
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug(Thread.currentThread() + " checking channel with key " + messageChannelKey + " : " + foundMessageChannel + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
        
        if (foundMessageChannel != null) {
            foundMessageChannel.setKeepAliveTimeout(keepAliveTimeout);
            return true;
        }
        
        // foundMessageChannel = incomingMessageChannels.get(messageChannelKey);
        
        // if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
        //     logger.logDebug(Thread.currentThread() + " checking incoming channel with key " + messageChannelKey + " : " + foundMessageChannel + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
        
        // if (foundMessageChannel != null) {
        //     foundMessageChannel.setKeepAliveTimeout(keepAliveTimeout);
        //     return true;
        // }

        return false;
    }       

    protected void validatePortInRange(int port) throws IllegalArgumentException {
        if (port < 1 || port > 65535){
            throw new IllegalArgumentException("Peer port should be greater than 0 and less 65535, port = " + port);
        }
    }

    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[] { 
      new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() { 
          return new X509Certificate[0]; 
        }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug(
                        "checkClientTrusted : Not validating certs " + certs + " authType " + authType);
            }
        }
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug(
                        "checkServerTrusted : Not validating certs " + certs + " authType " + authType);
            }
        }
    }};
}
