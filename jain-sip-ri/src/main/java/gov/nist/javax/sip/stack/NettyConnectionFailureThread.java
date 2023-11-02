package gov.nist.javax.sip.stack;

public class NettyConnectionFailureThread implements Runnable {
	NettyStreamMessageChannel messageChannel;
	public NettyConnectionFailureThread(NettyStreamMessageChannel messageChannel) {
		this.messageChannel = messageChannel;
	}

	public void run() {
		messageChannel.triggerConnectFailure();
	}
}