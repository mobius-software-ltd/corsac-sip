package test.unit.gov.nist.javax.sip.stack.udpnio;

import java.io.IOException;
import java.net.InetAddress;

import javax.sip.ListeningPoint;
import javax.sip.SipProvider;

import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyDatagramMessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * Netty message processor factory whose UDP processor runs on the NIO transport even
 * where native epoll is available, as on a JVM without the epoll native library.
 */
public class NioUdpMessageProcessorFactory extends NettyMessageProcessorFactory {

    @Override
    public MessageProcessor createMessageProcessor(SIPTransactionStack sipStack, InetAddress ipAddress, int port,
            String transport) throws IOException {
        if (transport.equalsIgnoreCase(ListeningPoint.UDP)) {
            return new NioUdpMessageProcessor(ipAddress, sipStack, port);
        }
        return super.createMessageProcessor(sipStack, ipAddress, port, transport);
    }

    /** how many UDP channels the NIO processor behind the provider bound */
    static int boundChannels(SipProvider sipProvider) {
        ListeningPointImpl listeningPoint = (ListeningPointImpl) sipProvider.getListeningPoint(ListeningPoint.UDP);
        return ((NioUdpMessageProcessor) listeningPoint.getMessageProcessor()).boundChannels();
    }

    static class NioUdpMessageProcessor extends NettyDatagramMessageProcessor {

        NioUdpMessageProcessor(InetAddress ipAddress, SIPTransactionStack sipStack, int port) throws IOException {
            super(ipAddress, sipStack, port, new NioEventLoopGroup(sipStack.getThreadPoolSize()));
        }

        @Override
        public Class<? extends DatagramChannel> nioOrEpollServerDatagramChannel() {
            return NioDatagramChannel.class;
        }

        int boundChannels() {
            return serverChannels.size();
        }

        @Override
        public void stop() {
            super.stop();
            eventLoopGroup.shutdownGracefully();
        }
    }
}
