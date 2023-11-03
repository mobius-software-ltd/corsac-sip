package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.ParseException;

import javax.sip.ListeningPoint;
import javax.sip.address.Hop;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.InternalErrorHandler;
import gov.nist.core.LogWriter;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;

public class NettyDatagramMessageChannel extends MessageChannel implements RawMessageChannel {    
    private static StackLogger logger = CommonLogger.getLogger(NettyDatagramMessageChannel.class);

    private final SIPTransactionStack sipStack;
    private Channel channel;    

    private String myAddress;

    /**
     * Where we got the stuff from
     */
    private InetAddress peerAddress;

    private int peerPacketSourcePort;

    private InetAddress peerPacketSourceAddress;

    /**
     * Reciever port -- port of the destination.
     */
    private int peerPort;

    private String peerProtocol;

    protected int myPort;

    private long receptionTime;
        
    public NettyDatagramMessageChannel(Channel channel, NettyDatagramMessageProcessor nettyUDPMessageProcessor) {
        super.messageProcessor = nettyUDPMessageProcessor;
        this.sipStack = messageProcessor.sipStack;        

        this.myAddress = messageProcessor.getIpAddress().getHostAddress();
        this.myPort = messageProcessor.getPort();
        
        this.channel = channel;        
    }    

    public NettyDatagramMessageChannel(Channel channel, NettyDatagramMessageProcessor nettyUDPMessageProcessor,
            InetAddress targetHost, int port) {
        this(channel, nettyUDPMessageProcessor);            
        
        this.peerAddress = targetHost;
        this.peerPort = port;
    }

    @Override
    public void sendMessage(SIPMessage sipMessage) throws IOException {
        ByteBuf byteBuf = Unpooled.copiedBuffer(sipMessage.encodeAsBytes(this.getTransport()));
        channel.writeAndFlush(new DatagramPacket(byteBuf, new InetSocketAddress(peerAddress, peerPort)));
    }

     @Override
    protected void sendMessage(byte[] message, InetAddress receiverAddress, int receiverPort, boolean reconnectFlag)
            throws IOException {
        ByteBuf byteBuf = Unpooled.copiedBuffer(message);
        channel.writeAndFlush(new DatagramPacket(byteBuf, new InetSocketAddress(receiverAddress, receiverPort)));
    }

    @Override
    public void processMessage(SIPMessage sipMessage) throws Exception {
        this.receptionTime = System.currentTimeMillis();
        
        // FIXME : check if needed
        // if (sipMessage == null) {
        //     if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
        //         logger.logDebug(
        //                 "Rejecting message !  + Null message parsed.");
        //     }
            
        //     String key = packet.getAddress().getHostAddress() + ":"
        //             + packet.getPort();
        //     if (pingBackRecord.get(key) == null
        //             && sipStack.getMinKeepAliveInterval() > 0) {
        //         byte[] retval = "\r\n\r\n".getBytes();
        //         DatagramPacket keepalive = new DatagramPacket(retval, 0,
        //                 retval.length, packet.getAddress(), packet.getPort());
        //         PingBackTimerTask task = new PingBackTimerTask(packet
        //                 .getAddress().getHostAddress(), packet.getPort());
        //         UDPMessageChannel.pingBackRecord.put(key, task);
        //         this.sipStack.getTimer().schedule(task,
        //                 sipStack.getMinKeepAliveInterval() * 1000);
        //         ((UDPMessageProcessor) this.messageProcessor).sock
        //                 .send(keepalive);
        //     } else {
        //         logger.logDebug("Not sending ping back");
        //     // }String key = packet.getAddress().getHostAddress() + ":"
        //     + packet.getPort();
        //     if (pingBackRecord.get(key) == null
        //             && sipStack.getMinKeepAliveInterval() > 0) {
        //         byte[] retval = "\r\n\r\n".getBytes();
        //         DatagramPacket keepalive = new DatagramPacket(retval, 0,
        //                 retval.length, packet.getAddress(), packet.getPort());
        //         PingBackTimerTask task = new PingBackTimerTask(packet
        //                 .getAddress().getHostAddress(), packet.getPort());
        //         UDPMessageChannel.pingBackRecord.put(key, task);
        //         this.sipStack.getTimer().schedule(task,
        //                 sipStack.getMinKeepAliveInterval() * 1000);
        //         ((UDPMessageProcessor) this.messageProcessor).sock
        //                 .send(keepalive);
        //     // } else {
        //     return;
        // }
        Via topMostVia = sipMessage.getTopmostVia();
        // Check for the required headers.
        if (sipMessage.getFrom() == null || sipMessage.getTo() == null
                || sipMessage.getCallId() == null
                || sipMessage.getCSeq() == null || topMostVia == null) {
            String badmsg = new String(sipMessage.encodeAsBytes(ListeningPoint.UDP));
            if (logger.isLoggingEnabled()) {
                logger
                        .logError("bad message " + badmsg);
                logger.logError(
                        ">>> Dropped Bad Msg " + "From = "
                                + sipMessage.getFrom() + "To = "
                                + sipMessage.getTo() + "CallId = "
                                + sipMessage.getCallId() + "CSeq = "
                                + sipMessage.getCSeq() + "Via = "
                                + sipMessage.getViaHeaders());
            }
            return;
        }

        if (sipMessage instanceof SIPRequest) {
            String sipVersion = ((SIPRequest)sipMessage).getRequestLine().getSipVersion();
            if (! sipVersion.equals("SIP/2.0")) {
                    Response versionNotSupported = ((SIPRequest) sipMessage).createResponse(Response.VERSION_NOT_SUPPORTED, "Bad version " + sipVersion);
                    this.sendMessage(versionNotSupported.toString().getBytes(),sipMessage.getPeerPacketSourceAddress(),sipMessage.getPeerPacketSourcePort(),false);
                    return;
            }
            String method = ((SIPRequest) sipMessage).getMethod();
            String cseqMethod = ((SIPRequest) sipMessage).getCSeqHeader()
                    .getMethod();

            if (!method.equalsIgnoreCase(cseqMethod)) {
                SIPResponse sipResponse = ((SIPRequest) sipMessage)
                .createResponse(SIPResponse.BAD_REQUEST);
                byte[] resp = sipResponse
                        .encodeAsBytes(this.getTransport());
                this.sendMessage(resp,sipMessage.getPeerPacketSourceAddress(),sipMessage.getPeerPacketSourcePort(),false);
                return;

            }
        }

        if(sipStack.sipEventInterceptor != null) {
            sipStack.sipEventInterceptor.beforeMessage(sipMessage);
        }
        // For a request first via header tells where the message
        // is coming from.
        // For response, just get the port from the packet.
        if (sipMessage instanceof SIPRequest) {
            Hop hop = sipStack.addressResolver.resolveAddress(topMostVia
                    .getHop());
            this.peerPort = hop.getPort();
            this.peerProtocol = topMostVia.getTransport();

            this.peerPacketSourceAddress = sipMessage.getPeerPacketSourceAddress();
            this.peerPacketSourcePort = sipMessage.getPeerPacketSourcePort();
            try {
                this.peerAddress = sipMessage.getRemoteAddress();
                // Check to see if the received parameter matches
                // the peer address and tag it appropriately.
                
                boolean hasRPort = topMostVia.hasParameter(Via.RPORT);
                if(sipStack.isPatchRport()) {
                    if(!hasRPort && topMostVia.getPort() != peerPacketSourcePort) {
                        // https://github.com/RestComm/jain-sip/issues/79
                        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                            logger.logDebug(
                                    "setting rport since viaPort " + topMostVia.getPort() + " different than peerPacketSourcePort " 
                                    + peerPacketSourcePort + " so that the response can be routed back");
                        }
                        hasRPort = true;
                    }
                }
                if (hasRPort
                        || !hop.getHost().equals(
                                this.peerAddress.getHostAddress())) {
                    topMostVia.setParameter(Via.RECEIVED, this.peerAddress
                            .getHostAddress());
                }

                if (hasRPort) {
                    topMostVia.setParameter(Via.RPORT, Integer
                            .toString(this.peerPacketSourcePort));
                }
            } catch (java.text.ParseException ex1) {
                InternalErrorHandler.handleException(ex1);
            }

        } else {

            this.peerPacketSourceAddress = sipMessage.getPeerPacketSourceAddress();
            this.peerPacketSourcePort = sipMessage.getPeerPacketSourcePort();
            this.peerAddress = sipMessage.getRemoteAddress();
            this.peerPort = sipMessage.getRemotePort();
            this.peerProtocol = topMostVia.getTransport();
        }

        this.processSIPMessage(sipMessage);
        if(sipStack.sipEventInterceptor != null) {
            sipStack.sipEventInterceptor.afterMessage(sipMessage);
        }        
    }

    private void processSIPMessage(SIPMessage sipMessage) {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("received new UDP message from: " + this.peerAddress.getHostAddress() + ":" + this.peerPort + " msg: " + sipMessage);
        }

        sipMessage.setRemoteAddress(this.peerAddress);
        sipMessage.setRemotePort(this.getPeerPort());
        sipMessage.setLocalPort(this.getPort());
        sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
        //Issue 3: https://telestax.atlassian.net/browse/JSIP-3
        if(logger.isLoggingEnabled(LogWriter.TRACE_INFO)) {
        	logger.logInfo("Setting SIPMessage peerPacketSource to: "+peerPacketSourceAddress+":"+peerPacketSourcePort);
        }
        sipMessage.setPeerPacketSourceAddress(this.peerPacketSourceAddress);
        sipMessage.setPeerPacketSourcePort(this.peerPacketSourcePort);



        if (sipMessage instanceof SIPRequest) {
            SIPRequest sipRequest = (SIPRequest) sipMessage;

            // This is a request - process it.
            // So far so good -- we will commit this message if
            // all processing is OK.
            if (logger.isLoggingEnabled(
                    ServerLogger.TRACE_MESSAGES)) {

                this.sipStack.serverLogger.logMessage(sipMessage, this
                        .getPeerHostPort().toString(), this.getHost() + ":"
                        + this.myPort, false, receptionTime);

            }
            final ServerRequestInterface sipServerRequest = sipStack
                    .newSIPServerRequest(sipRequest, this);
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
                sipServerRequest.processRequest(sipRequest, this);
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

                this.sipStack.serverLogger.logMessage(sipResponse, this
                        .getPeerHostPort().toString(), this.getHost() + ":"
                        + this.myPort, false, receptionTime);

            }
            ServerResponseInterface sipServerResponse = sipStack
                    .newSIPServerResponse(sipResponse, this);
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

                    sipServerResponse.processResponse(sipResponse, this);
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
    
    @Override
    public void close() {
        channel.closeFuture().awaitUninterruptibly();
    }

    @Override
    public SIPTransactionStack getSIPStack() {
        return this.sipStack;
    }

    @Override
    public String getTransport() {
       return ListeningPoint.UDP;
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public String getPeerAddress() {
        return peerAddress.getHostAddress();
    }

    @Override
    protected InetAddress getPeerInetAddress() {
        return peerAddress;
    }

    @Override
    protected String getPeerProtocol() {
        return ListeningPoint.UDP;
    }

    @Override
    public int getPeerPort() {
        return peerPort;
    }

    @Override
    public int getPeerPacketSourcePort() {
        return peerPacketSourcePort;
    }

    @Override
    public InetAddress getPeerPacketSourceAddress() {
        return peerPacketSourceAddress;
    }

    @Override
    public String getKey() {
        return getKey(peerAddress, peerPort, "UDP");
    }

    
    /**
     * Get the logical originator of the message (from the top via header).
     *
     * @return topmost via header sentby field
     */
    @Override
    public String getViaHost() {
        return this.myAddress;
    }

    /**
     * Get the logical port of the message orginator (from the top via hdr).
     *
     * @return the via port from the topmost via header.
     */
    @Override
    public int getViaPort() {
        return this.myPort;
    }

    /**
     * Compare two UDP Message channels for equality.
     *
     * @param other
     *            The other message channel with which to compare oursleves.
     */
    public boolean equals(Object other) {

        if (other == null)
            return false;
        boolean retval;
        if (!this.getClass().equals(other.getClass())) {
            retval = false;
        } else {
            UDPMessageChannel that = (UDPMessageChannel) other;
            retval = this.getKey().equals(that.getKey());
        }

        return retval;
    }   
}
