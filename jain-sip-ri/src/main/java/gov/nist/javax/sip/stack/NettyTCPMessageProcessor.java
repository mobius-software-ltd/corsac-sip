package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetAddress;

import javax.sip.ListeningPoint;

import gov.nist.core.CommonLogger;
import gov.nist.core.HostPort;
import gov.nist.core.StackLogger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Sit in a loop and handle incoming udp datagram messages. For each Datagram
 * packet, a new UDPMessageChannel is created (upto the max thread pool size).
 * Each UDP message is processed in its own thread).
 *
 * @version 1.0
 *
 * @author Jean Deruelle  <br/>
 */
public class NettyTCPMessageProcessor extends MessageProcessor{
	
	private static StackLogger logger = CommonLogger.getLogger(NettyTCPMessageProcessor.class);

    /**
     * The Mapped port (in case STUN suport is enabled) 
     */
    private int port;

    // multithreaded event loop that handles incoming connection and I/O operations    
    EventLoopGroup bossGroup; 
    // multithreaded event loop that handles I/O operation and handles the traffic 
    // of the accepted connection once the boss accepts the connection
    // and registers the accepted connection to the worker
    EventLoopGroup workerGroup;
    /**
     * Constructor.
     *
     * @param sipStack pointer to the stack.
     */
    protected NettyTCPMessageProcessor(InetAddress ipAddress,
            SIPTransactionStack sipStack, int port) throws IOException {
                super(ipAddress, port, ListeningPoint.TCP, sipStack);
                this.port = port;
                // TODO: Add how many threads can be used
                this.bossGroup = new NioEventLoopGroup(); 
                // TODO: Add how many threads can be used
                this.workerGroup = new NioEventLoopGroup();
    }

    @Override
    public MessageChannel createMessageChannel(HostPort targetHostPort) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createMessageChannel'");
    }

    @Override
    public MessageChannel createMessageChannel(InetAddress targetHost, int port) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createMessageChannel'");
    }

    @Override
    public void start() throws IOException {
        try {
            // helper class that sets up a server. We can set up the server using a Channel directly. 
            // However, please note that this is a tedious process, we do not need to do that for now.
            // TODO: May be revisited later for performance/optimizations reasons
            ServerBootstrap b = new ServerBootstrap(); 
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) 
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new NettyChannelInitializer(sipStack, this))
             // TODO Add Option based on sip stack config
             .option(ChannelOption.SO_BACKLOG, 128) // for the NioServerSocketChannel that accepts incoming connections.
             .childOption(ChannelOption.SO_KEEPALIVE, true); // for the Channels accepted by the parent ServerChannel, which is NioSocketChannel in this case
            
            // Bind and start to accept incoming connections.
            Channel channel = b.bind(port).sync().channel();
            
            // Create a new thread to run the server
            new Thread(() -> {
                try {
                    // Wait until the server socket is closed.
                    // In this example, this does not happen, but you can do that to gracefully
                    // shut down your server.
                    System.out.println("TCP Server started on port " + port);
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
        return this.port;
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
}
