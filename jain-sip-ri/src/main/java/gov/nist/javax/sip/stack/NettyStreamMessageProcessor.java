package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * Sit in a loop and handle incoming udp datagram messages. For each Datagram
 * packet, a new UDPMessageChannel is created (upto the max thread pool size).
 * Each UDP message is processed in its own thread).
 *
 * @version 1.0
 *
 * @author Jean Deruelle  <br/>
 */
public class NettyStreamMessageProcessor extends MessageProcessor{
	
	private static StackLogger logger = CommonLogger.getLogger(NettyStreamMessageProcessor.class);

    protected final Map<String, NettyStreamMessageChannel> messageChannels;
    
    // multithreaded event loop that handles incoming connection and I/O operations    
    EventLoopGroup bossGroup; 
    // multithreaded event loop that handles I/O operation and handles the traffic 
    // of the accepted connection once the boss accepts the connection
    // and registers the accepted connection to the worker
    EventLoopGroup workerGroup;

    NettyMessageHandler context;

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
                // TODO: Add how many threads can be used
                this.bossGroup = new NioEventLoopGroup(); 
                // TODO: Add how many threads can be used
                this.workerGroup = new NioEventLoopGroup();
                if(transport.equals(ListeningPoint.TLS)) {
                    if(sipStack.getClientAuth() == ClientAuthType.DisabledAll) {
                        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                            logger.logDebug(
                                    "ClientAuth " + sipStack.getClientAuth()  +  " bypassing all cert validations");
                        }                        
                        this.sslServerContext = SslContextBuilder.forServer(sipStack.securityManagerProvider.getKeyManagers(false)[0]).trustManager(trustAllCerts[0]).build();
                        this.sslClientContext = SslContextBuilder.forClient().keyManager(sipStack.securityManagerProvider.getKeyManagers(true)[0]).trustManager(trustAllCerts[0]).build();                        
                    } else {
                        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                            logger.logDebug(
                                    "ClientAuth " + sipStack.getClientAuth());
                        }
                        this.sslServerContext = SslContextBuilder.forServer(sipStack.securityManagerProvider.getKeyManagers(false)[0]).trustManager(sipStack.securityManagerProvider.getTrustManagers(false)[0]).build();
                        this.sslClientContext = SslContextBuilder.forClient().keyManager(sipStack.securityManagerProvider.getKeyManagers(true)[0]).trustManager(sipStack.securityManagerProvider.getTrustManagers(true)[0]).build();                                                
                    }
                }
    }

    /**
     * This private version is thread safe using proper critical session.
     * 
     * We don't use putIfAbset from CHM since creating a channel instance itself
     * is quite heavy. See https://github.com/RestComm/jain-sip/issues/80.
     * 
     * Using synchronized at method level, instead of any internal att, 
     * as we had in non Nio impl. This is better than use sync section with 
     * non-volatile variable. 
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
    		logger.logDebug("NioTcpMessageProcessor::createMessageChannel: " + targetHostPort);
    	}
        MessageChannel retval = null;
    	try {
    		String key = MessageChannel.getKey(targetHostPort, transport);
		    // retval = messageChannels.get(key);
                //here we use double-checked locking trying to reduce contention	
    		if (retval == null) {
                    retval = createMessageChannel(key, 
                            targetHostPort.getInetAddress(), targetHostPort.getPort());  			
		}    		
    	} finally {
    		if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    			logger.logDebug("MessageChannel::createMessageChannel - exit " + retval);
    		}
    	}
        return retval;
    }
    
    public NettyStreamMessageChannel createMessageChannel(Channel channel) {
        
        InetSocketAddress socketAddress = ((InetSocketAddress)channel.remoteAddress());
    	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    		logger.logDebug("NioTcpMessageProcessor::createMessageChannel: " + socketAddress.getAddress().getHostAddress()+":"+socketAddress.getPort());
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
        // MessageChannel retval = messageChannels.get(key);
        MessageChannel retval = null;
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
            // helper class that sets up a server. We can set up the server using a Channel directly. 
            // However, please note that this is a tedious process, we do not need to do that for now.
            // TODO: May be revisited later for performance/optimizations reasons            
            ServerBootstrap server = new ServerBootstrap(); ; 
            server.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) 
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new NettyChannelInitializer(this, sslServerContext))
             // TODO Add Option based on sip stack config
             .option(ChannelOption.SO_BACKLOG, 128) // for the NioServerSocketChannel that accepts incoming connections.
             .childOption(ChannelOption.SO_KEEPALIVE, true); // for the Channels accepted by the parent ServerChannel, which is NioSocketChannel in this case
            
            // Bind and start to accept incoming connections.
            Channel channel = server.bind(port).sync().channel();
            
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

    protected synchronized void remove(NettyStreamMessageChannel messageChannel) {

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
	
    protected synchronized void cacheMessageChannel(NettyStreamMessageChannel messageChannel) {
        String key = messageChannel.getKey();
        NettyStreamMessageChannel currentChannel = messageChannels.get(key);
        if (currentChannel != null) {
            // FIXME: should we close the channel here ? This is making the testsuite fail with Netty
            // if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG))
            //     logger.logDebug("Closing " + key);
            // currentChannel.close();
        }
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("Caching " + key);
        this.messageChannels.put(key, messageChannel);
    }
    
    public boolean closeReliableConnection(String peerAddress, int peerPort) throws IllegalArgumentException {

        validatePortInRange(peerPort);

        HostPort hostPort = new HostPort();
        hostPort.setHost(new Host(peerAddress));
        hostPort.setPort(peerPort);

        String messageChannelKey = MessageChannel.getKey(hostPort, "TCP");

        synchronized (this) {
            NettyStreamMessageChannel foundMessageChannel = messageChannels.get(messageChannelKey);

            if (foundMessageChannel != null) {
                foundMessageChannel.close();
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                    logger.logDebug(Thread.currentThread() + " Removing channel " + messageChannelKey + " for processor " + getIpAddress()+ ":" + getPort() + "/" + getTransport());
                // incomingMessageChannels.remove(messageChannelKey);
                messageChannels.remove(messageChannelKey);
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
        }
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
            // foundMessageChannel.setKeepAliveTimeout(keepAliveTimeout);
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
