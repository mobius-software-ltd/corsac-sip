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
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.ServerRequestInterface;
import gov.nist.javax.sip.stack.ServerResponseInterface;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.RawMessageChannel;
import gov.nist.javax.sip.stack.transports.processors.oio.UDPMessageChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;

/**
 * Netty Based Datagram Transport Protocol (UDP) Message Channel to handle SIP Messages
 * 
 * @author Jean Deruelle
 */
public class NettyDatagramMessageChannel extends MessageChannel implements RawMessageChannel {    
    private static StackLogger logger = CommonLogger.getLogger(NettyDatagramMessageChannel.class);

    private final SIPTransactionStack sipStack;
    protected Channel channel;    

    private String myAddress;

    /**
     * Where we got the stuff from
     */
    protected InetAddress peerAddress;

    private int peerPacketSourcePort;

    private InetAddress peerPacketSourceAddress;

    /**
     * Reciever port -- port of the destination.
     */
    protected int peerPort;

    private String peerProtocol;

    protected int myPort;

    private long receptionTime;
        
    public NettyDatagramMessageChannel(Channel channel, NettyDatagramMessageProcessor nettyUDPMessageProcessor) {
        super.messageProcessor = nettyUDPMessageProcessor;
        this.sipStack = messageProcessor.getSIPStack();        

        this.myAddress = messageProcessor.getIpAddress().getHostAddress();
        this.myPort = messageProcessor.getPort();
        if(channel.remoteAddress() != null) {
            setPeerAddress(((InetSocketAddress) channel.remoteAddress()).getAddress());
            setPeerPort(((InetSocketAddress) channel.remoteAddress()).getPort());
            this.peerProtocol = nettyUDPMessageProcessor.getTransport();
        }
        
        this.channel = channel;        
    }    

    public NettyDatagramMessageChannel(Channel channel, NettyDatagramMessageProcessor nettyUDPMessageProcessor,
            InetAddress targetHost, int port) {
        this(channel, nettyUDPMessageProcessor);            
        
        setPeerAddress(targetHost);
        setPeerPort(port);        
        this.peerProtocol = nettyUDPMessageProcessor.getTransport();
    }

    @Override
    public void sendMessage(SIPMessage sipMessage) throws IOException {

        // Test and see where we are going to send the messsage. If the message
        // is sent back to oursleves, just
        // shortcircuit processing.
        long time = System.currentTimeMillis();
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("sending new UDP message to: " + getPeerAddress() + ":" + getPeerPort() + "/" + peerProtocol);
        }
        //check for self routing
        MessageProcessor messageProcessor = getSIPStack().findMessageProcessor(getPeerAddress(), getPeerPort(), peerProtocol);
        if(messageProcessor != null) {
            RawMessageChannel messageChannel = (RawMessageChannel) messageProcessor.createMessageChannel(
                    this.getPeerInetAddress(), this.getPeerPort());
            getSIPStack().selfRouteMessage(messageChannel, sipMessage);
            return;            
        }
        try {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(sipMessage.encodeAsBytes(this.getTransport()));
            channel.writeAndFlush(new DatagramPacket(byteBuf, new InetSocketAddress(getPeerInetAddress(), getPeerPort())));

            // we didn't run into problems while sending so let's set ports and
            // addresses before feeding the message to the loggers.
            sipMessage.setRemoteAddress(getPeerInetAddress());
            sipMessage.setRemotePort(getPeerPort());
            sipMessage.setLocalPort(this.getPort());
            sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
        } catch (Exception ex) {
            logger.logError(
                    "An exception occured while sending message", ex);
            throw new IOException("An exception occured while sending message");
        } finally {
            if (logger.isLoggingEnabled(
                    ServerLogger.TRACE_MESSAGES)
                    && !sipMessage.isNullRequest())
                logMessage(sipMessage, getPeerInetAddress(), getPeerPort(), time);
            else if (logger.isLoggingEnabled(
                    ServerLogger.TRACE_DEBUG))
                logger.logDebug("Sent EMPTY Message");
        }
        
    }

     @Override
     public void sendMessage(byte[] message, InetAddress receiverAddress, int receiverPort, boolean reconnectFlag)
            throws IOException {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("sending new UDP message to: " + receiverAddress + ":" + receiverPort + " msg: " + new String(message));
        }
        ByteBuf byteBuf = Unpooled.wrappedBuffer(message);
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
                SIPResponse sipResponse = ((SIPRequest) sipMessage).createResponse(SIPResponse.BAD_REQUEST);
                byte[] resp = sipResponse
                        .encodeAsBytes(this.getTransport());
                this.sendMessage(resp,sipMessage.getPeerPacketSourceAddress(),sipMessage.getPeerPacketSourcePort(),false);
                return;

            }
        }
        
        // For a request first via header tells where the message
        // is coming from.
        // For response, just get the port from the packet.
        if (sipMessage instanceof SIPRequest) {
            Hop hop = sipStack.getAddressResolver().resolveAddress(topMostVia
                    .getHop());
            this.setPeerPort(hop.getPort());
            this.peerProtocol = topMostVia.getTransport();
            if(this.peerPacketSourceAddress == null || this.peerPacketSourcePort <= 0) {
                this.peerPacketSourceAddress = sipMessage.getPeerPacketSourceAddress();
                this.peerPacketSourcePort = sipMessage.getPeerPacketSourcePort();
            }
            if(this.getPeerAddress() == null) {
                this.setPeerAddress(sipMessage.getRemoteAddress());
            }
            try {
                
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
                                this.getPeerAddress())) {
                    topMostVia.setParameter(Via.RECEIVED, this.getPeerAddress());
                }

                if (hasRPort) {
                    topMostVia.setParameter(Via.RPORT, Integer
                            .toString(this.peerPacketSourcePort));
                }
            } catch (java.text.ParseException ex1) {
                InternalErrorHandler.handleException(ex1);
            }

        } else {
            if(sipMessage.getPeerPacketSourceAddress() != null) {
                this.peerPacketSourceAddress = sipMessage.getPeerPacketSourceAddress();
            }
            if(sipMessage.getPeerPacketSourceAddress() != null) {
                this.peerPacketSourcePort = sipMessage.getPeerPacketSourcePort();
            }
            if(sipMessage.getRemoteAddress() != null) {
                this.setPeerAddress(sipMessage.getRemoteAddress());
            }
            if(sipMessage.getRemotePort() > 0) {
                this.setPeerPort(sipMessage.getRemotePort());
            }
            if(topMostVia.getTransport() != null) {
                this.peerProtocol = topMostVia.getTransport();
            }            
        }

        this.processSIPMessage(sipMessage);        
    }

    private void processSIPMessage(SIPMessage sipMessage) {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("received new UDP message from: " + this.getPeerAddress() + ":" + this.getPeerPort() + " msg: " + sipMessage);
        }

        sipMessage.setRemoteAddress(this.getPeerInetAddress());
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

                this.sipStack.getServerLogger().logMessage(sipMessage, this
                        .getPeerHostPort().toString(), this.getHost() + ":"
                        + this.myPort, false, receptionTime);

            }
            final ServerRequestInterface sipServerRequest = sipStack
                    .newSIPServerRequest(sipRequest, messageProcessor.getListeningPoint().getProvider(), this);
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
            // try {
                sipServerRequest.processRequest(sipRequest, this);
            // } finally {
            //     if (sipServerRequest instanceof SIPTransaction) {
            //         SIPServerTransaction sipServerTx = (SIPServerTransaction) sipServerRequest;
            //         if (!sipServerTx.passToListener()) {
            //             ((SIPTransaction) sipServerRequest).releaseSem();
            //         }
            //     }
            // }
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

                this.sipStack.getServerLogger().logMessage(sipResponse, this
                        .getPeerHostPort().toString(), this.getHost() + ":"
                        + this.myPort, false, receptionTime);

            }
            ServerResponseInterface sipServerResponse = sipStack
                    .newSIPServerResponse(sipResponse, this);
            if (sipServerResponse != null) {
                // try {
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
                // } finally {
                //     if (sipServerResponse instanceof SIPTransaction
                //             && !((SIPTransaction) sipServerResponse)
                //                     .passToListener())
                //         ((SIPTransaction) sipServerResponse).releaseSem();
                // }

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
        // Don't close the channel even if
        // we don't cache client connections 
        // as we currently reuse the same underlying
        // epoll netty channels for all connections
        // channel.closeFuture().await();
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
    public InetAddress getPeerInetAddress() {
        return peerAddress;
    }

    protected void setPeerAddress(InetAddress peerAddress) {
		this.peerAddress = peerAddress;
	}
    
    @Override
    public String getPeerProtocol() {
        return ListeningPoint.UDP;
    }

    @Override
    public int getPeerPort() {
        return peerPort;
    }

    protected void setPeerPort(int peerPort) {
		this.peerPort = peerPort;
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
        return getKey(getPeerInetAddress(), getPeerPort(), "UDP");
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
