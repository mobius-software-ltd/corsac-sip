package gov.nist.javax.sip.stack;

import io.netty.channel.Channel;

public interface NettyMessageProcessor {

    public MessageChannel createMessageChannel(Channel channel);

    public String getTransport();
    public SIPTransactionStack getSIPStack();
}
