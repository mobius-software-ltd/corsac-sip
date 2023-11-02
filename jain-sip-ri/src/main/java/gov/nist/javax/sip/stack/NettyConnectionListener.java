package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sip.ListeningPoint;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
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
    private byte[] message;    

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
                    new ConnectionFailureThread(messageChannel.getEncapsulatedClientTransaction()) 
                );
            } else {
                triggerConnectFailure(messageChannel.getEncapsulatedClientTransaction());                                           
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
                try {
                    ChannelFuture future2 = channel.writeAndFlush(byteBuf).sync();
                    if (future2.isSuccess() == false) {
                        throw new IOException("Failed to send message " + new String(message));
                    }	
                } catch (InterruptedException e) {
                    logger.logError("Failed to send message " + byteBuf.toString(), e);
                    throw new IOException(e);
                }	
            }                									
        }            
    }

    public void triggerConnectFailure(SIPTransaction transaction) {
        //alert of IOException to pending Data TXs     
		// System.out.println("txId hello ? " + txId);   
		if(transaction != null) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("triggerConnectFailure transaction:" + transaction);    
			}
			if (transaction != null) {
				if (transaction instanceof SIPClientTransaction) {
					//8.1.3.1 Transaction Layer Errors
					if (transaction.getRequest() != null &&
							!transaction.getRequest().getMethod().equalsIgnoreCase("ACK"))
					{
						SIPRequest req = (SIPRequest) transaction.getRequest();
						SIPResponse unavRes = req.createResponse(Response.SERVICE_UNAVAILABLE, "Transport error sending request.");
						try {
								messageChannel.processMessage(unavRes);
						} catch (Exception e) {
							if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
								logger.logDebug("failed to report transport error", e);
							}
						}
					}
				} else {
					//17.2.4 Handling Transport Errors
					transaction.raiseIOExceptionEvent();
				}
			}
		}
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("triggerConnectFailure close");    
		}
        messageChannel.close(true, false);
	}

	class ConnectionFailureThread implements Runnable {
		SIPTransaction transaction;
		public ConnectionFailureThread(SIPTransaction transaction) {
			this.transaction = transaction;
		}

		public void run() {
			triggerConnectFailure(transaction);
		}
	}
}
