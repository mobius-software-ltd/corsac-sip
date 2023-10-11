package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.ParseException;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.MessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NettyMessageHandler extends SimpleChannelInboundHandler<byte[]> {
    private static StackLogger logger = CommonLogger.getLogger(NettyMessageHandler.class);

    private SIPTransactionStack sipStack;
    private NettyTCPMessageProcessor messageProcessor;
    private MessageParser smp = null;

    private ByteBuf buf;
    
    public NettyMessageHandler(SIPTransactionStack sipStack, NettyTCPMessageProcessor nettyTCPMessageProcessor) {
        this.sipStack = sipStack;
        this.messageProcessor = nettyTCPMessageProcessor;
        this.smp = sipStack.getMessageParserFactory().createMessageParser(sipStack);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        buf = ctx.alloc().buffer(4); // (1)
    }
    
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        buf.release(); // (1)
        buf = null;
    }
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, byte[] msg) {        
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("received following message: \n" + msg.toString());
        }
        
        SIPMessage sipMessage = null;
				
        try {
            // byte[] msgBytes = m.getBytes("UTF-8");
            sipMessage = smp.parseSIPMessage(msg, true, true, null);            
        } catch (ParseException e) {
            logger.logDebug(
                    "Parsing issue !  " + new String(msg.toString()) + " " + e.getMessage());
        }
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {   
            logger.logDebug(sipMessage.toString());
        }
        if(sipStack.getSelfRoutingThreadpoolExecutor() != null) {
            final String callId = sipMessage.getCallId().getCallId();
            
            try{                                                              
                if(callId == null || callId.trim().length() < 1) {
                    // http://code.google.com/p/jain-sip/issues/detail?id=18
                    // NIO Message with no Call-ID throws NPE
                    throw new IOException("received message with no Call-ID");
                }
                if(sipStack.sipEventInterceptor != null
            			// https://java.net/jira/browse/JSIP-503
                		&& sipMessage != null) {
            		sipStack.sipEventInterceptor.beforeMessage(sipMessage);
            	}

            	if(sipMessage != null) { // https://java.net/jira/browse/JSIP-503
            		processMessage(ctx, sipMessage);
            	}
            // } catch (ParseException e) {
            // 	// https://java.net/jira/browse/JSIP-499 move the ParseException here so the finally block 
            // 	// is called, the semaphore released and map cleaned up if need be
            // 	if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
            // 		logger.logDebug("Problem parsing message " + msg.toString() + " " + e.getMessage());
            // 	}
    		}catch (Exception e) {
            	logger.logError("Error occured processing message " + msg.toString(), e);
                // We do not break the TCP connection because other calls use the same socket here
            } finally {            
                if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                	logger.logDebug("releasing semaphore for message " + sipMessage);
                }
                if(sipStack.sipEventInterceptor != null
                		// https://java.net/jira/browse/JSIP-503
                		&& sipMessage != null) {
                	sipStack.sipEventInterceptor.afterMessage(sipMessage);
                }
            }
        } else {            
            try {
                processMessage(ctx, sipMessage);
            } catch (Exception e) {
                logger.logError("Can't process message " + msg.toString(), e);
            }
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Actually proces the parsed message.
     *
     * @param sipMessage
     * @throws IOException
     */
    public void processMessage(ChannelHandlerContext context, SIPMessage sipMessage) throws IOException {
        long receptionTime = System.currentTimeMillis();
        NettyTCPMessageChannel nettyTCPMessageChannel = new NettyTCPMessageChannel(messageProcessor, context);

        InetSocketAddress remoteAddress = (InetSocketAddress) context.channel().remoteAddress();
        sipMessage.setRemoteAddress(remoteAddress.getAddress());
        sipMessage.setRemotePort(remoteAddress.getPort());
        // FIXME: commented but should be fixed
        // sipMessage.setLocalPort(this.getPort());
        // sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
        //Issue 3: https://telestax.atlassian.net/browse/JSIP-3
        if(logger.isLoggingEnabled(LogWriter.TRACE_INFO)) {
        	logger.logInfo("Setting SIPMessage peerPacketSource to: "+remoteAddress.getAddress().getHostAddress()+":"+remoteAddress.getPort());
        }
        sipMessage.setPeerPacketSourceAddress(remoteAddress.getAddress());
        sipMessage.setPeerPacketSourcePort(remoteAddress.getPort());

        if (sipMessage instanceof SIPRequest) {
            SIPRequest sipRequest = (SIPRequest) sipMessage;
            
            // This is a request - process it.
            // So far so good -- we will commit this message if
            // all processing is OK.
            if (logger.isLoggingEnabled(
                    ServerLogger.TRACE_MESSAGES)) {
                
                this.sipStack.serverLogger.logMessage(sipMessage, nettyTCPMessageChannel
                        .getPeerHostPort().toString(), nettyTCPMessageChannel.getHost() + ":"
                        + nettyTCPMessageChannel.myPort, false, receptionTime);

            }
            final ServerRequestInterface sipServerRequest = sipStack
                    .newSIPServerRequest(sipRequest, nettyTCPMessageChannel);
            // Drop it if there is no request returned
            if (sipServerRequest == null) {
                if (logger.isLoggingEnabled()) {
                    logger
                            .logWarning(
                                    "Null request interface returned -- dropping request");
                }

                return;
            }
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug(
                        "About to process " + sipRequest.getFirstLine() + "/"
                                + sipServerRequest);
            try {
                sipServerRequest.processRequest(sipRequest, nettyTCPMessageChannel);
            } finally {
                if (sipServerRequest instanceof SIPTransaction) {
                    SIPServerTransaction sipServerTx = (SIPServerTransaction) sipServerRequest;
                    if (!sipServerTx.passToListener()) {
                        ((SIPTransaction) sipServerRequest).releaseSem();
                    }
                }
            }
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug(
                        "Done processing " + sipRequest.getFirstLine() + "/"
                                + sipServerRequest);

            // So far so good -- we will commit this message if
            // all processing is OK.

        } else {
            // Handle a SIP Reply message.
            SIPResponse sipResponse = (SIPResponse) sipMessage;
            try {
                sipResponse.checkHeaders();
            } catch (ParseException ex) {
                if (logger.isLoggingEnabled())
                    logger.logError(
                            "Dropping Badly formatted response message >>> "
                                    + sipResponse);
                return;
            }
            if (logger.isLoggingEnabled(
                    ServerLogger.TRACE_MESSAGES)) {

                this.sipStack.serverLogger.logMessage(sipResponse, nettyTCPMessageChannel
                        .getPeerHostPort().toString(), nettyTCPMessageChannel.getHost() + ":"
                        + nettyTCPMessageChannel.myPort, false, receptionTime);

            }
            ServerResponseInterface sipServerResponse = sipStack
                    .newSIPServerResponse(sipResponse, nettyTCPMessageChannel);
            if (sipServerResponse != null) {
                try {
                    if (sipServerResponse instanceof SIPClientTransaction
                            && !((SIPClientTransaction) sipServerResponse)
                                    .checkFromTag(sipResponse)) {
                        if (logger.isLoggingEnabled())
                            logger.logError(
                                    "Dropping response message with invalid tag >>> "
                                            + sipResponse);
                        return;
                    }

                    sipServerResponse.processResponse(sipResponse, nettyTCPMessageChannel);
                } finally {
                    if (sipServerResponse instanceof SIPTransaction
                            && !((SIPTransaction) sipServerResponse)
                                    .passToListener())
                        ((SIPTransaction) sipServerResponse).releaseSem();
                }

                // Normal processing of message.
            } else {
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug(
                            "null sipServerResponse as could not acquire semaphore or the valve dropped the message.");
                }
            }

        }
    }

}
