package gov.nist.javax.sip.stack.transports.processors.netty;

import java.io.IOException;
import java.net.InetAddress;

import gov.nist.core.HostPort;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import io.netty.channel.Channel;

public class NettyWebsocketsMessageProcessor extends MessageProcessor implements NettyMessageProcessor {

    NettyWebsocketsMessageProcessor(InetAddress ipAddress, SIPTransactionStack sipStack, int port) {
        super(ipAddress, port, "WS", sipStack);        
    }

    @Override
    public MessageChannel createMessageChannel(Channel channel) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createMessageChannel'");
    }

    @Override
    public SIPTransactionStack getSIPStack() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSIPStack'");
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'stop'");
    }

    @Override
    public int getDefaultTargetPort() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDefaultTargetPort'");
    }

    @Override
    public boolean isSecure() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isSecure'");
    }

    @Override
    public int getMaximumMessageSize() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMaximumMessageSize'");
    }

    @Override
    public boolean inUse() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'inUse'");
    }
    
}
