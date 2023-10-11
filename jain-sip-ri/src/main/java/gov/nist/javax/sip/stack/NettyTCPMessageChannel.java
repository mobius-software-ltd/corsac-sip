/*
 * Conditions Of Use
 *
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 Untied States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 *
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 *
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement
 *
 * .
 *
 */
package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.ParseException;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import io.netty.channel.ChannelHandlerContext;

public class NettyTCPMessageChannel extends MessageChannel {
	private static StackLogger logger = CommonLogger
			.getLogger(NioTcpMessageChannel.class);

	protected ChannelHandlerContext context;
	protected long lastActivityTimeStamp;
    
	protected String myAddress;

    protected int myPort;

    protected InetAddress peerAddress;
    
    // This is the port and adress that we will find in the headers of the messages from the peer
    protected int peerPortAdvertisedInHeaders = -1;
    protected String peerAddressAdvertisedInHeaders;
    
    protected int peerPort;

    protected String peerProtocol;
	

	protected NettyTCPMessageChannel(NettyTCPMessageProcessor nettyTCPMessageProcessor,
			ChannelHandlerContext context) throws IOException {				
		try {
			this.context = context;
			this.messageProcessor = nettyTCPMessageProcessor;
			this.peerAddress = ((InetSocketAddress) context.channel().remoteAddress()).getAddress();
			this.peerPort = ((InetSocketAddress) context.channel().remoteAddress()).getPort();									
			this.peerProtocol = nettyTCPMessageProcessor.transport;
			lastActivityTimeStamp = System.currentTimeMillis();			

            myAddress = nettyTCPMessageProcessor.getIpAddress().getHostAddress();
            myPort = nettyTCPMessageProcessor.getPort();

		} finally {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("Done creating NioTcpMessageChannel " + this + " socketChannel = " +context);
			}
		}

	}

	// public NioTcpMessageChannel(InetAddress inetAddress, int port,
	// 		SIPTransactionStack sipStack,
	// 		NioTcpMessageProcessor nioTcpMessageProcessor) throws IOException {		
	// 	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
	// 		logger.logDebug("NioTcpMessageChannel::NioTcpMessageChannel: "
	// 			+ inetAddress.getHostAddress() + ":" + port);
	// 	}
	// 	try {
	// 		messageProcessor = nioTcpMessageProcessor;
	// 		// Take a cached socket to the destination, if none create a new one and cache it
	// 		socketChannel = nioTcpMessageProcessor.nioHandler.createOrReuseSocket(
	// 				inetAddress, port);
	// 		peerAddress = socketChannel.socket().getInetAddress();
	// 		peerPort = socketChannel.socket().getPort();
	// 		super.mySock = socketChannel.socket();
	// 		peerProtocol = getTransport();
	// 		nioParser = new NioPipelineParser(sipStack, this,
	// 				this.sipStack.getMaxMessageSize());
	// 		NIOHandler nioHandler = nioTcpMessageProcessor.nioHandler;
	// 		nioHandler.putMessageChannel(socketChannel, this);
	// 		lastActivityTimeStamp = System.currentTimeMillis();
	// 		super.key = MessageChannel.getKey(peerAddress, peerPort, getTransport());

    //         myAddress = nioTcpMessageProcessor.getIpAddress().getHostAddress();
    //         myPort = nioTcpMessageProcessor.getPort();


	// 	} finally {
	// 		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
	// 			logger.logDebug("NioTcpMessageChannel::NioTcpMessageChannel: Done creating NioTcpMessageChannel "
	// 					+ this + " socketChannel = " + socketChannel);
	// 		}
	// 	}
	// }


	// @Override
	// protected void close(boolean removeSocket, boolean stopKeepAliveTask) {
		// try {
		// 	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
		// 		logger.logDebug("Closing NioTcpMessageChannel "
		// 				+ this + " socketChannel = " + socketChannel);
		// 	}
		// 	NIOHandler nioHandler = ((NioTcpMessageProcessor) messageProcessor).nioHandler;
		// 	nioHandler.removeMessageChannel(socketChannel);
		// 	if(socketChannel != null) {
		// 		socketChannel.close();
		// 	}
		// 	if(nioParser != null) {
		// 		nioParser.close();
		// 	}
		// 	this.isRunning = false;
		// 	if(removeSocket) {
		// 		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
		// 			logger.logDebug("Removing NioTcpMessageChannel "
		// 					+ this + " socketChannel = " + socketChannel);
		// 		}
		// 		((NioTcpMessageProcessor) this.messageProcessor).nioHandler.removeSocket(socketChannel);
		// 		((ConnectionOrientedMessageProcessor) this.messageProcessor).remove(this);
		// 	}
		// 	if(stopKeepAliveTask) {
		// 		cancelPingKeepAliveTimeoutTaskIfStarted();
		// 	}
		// } catch (IOException e) {
		// 	logger.logError("Problem occured while closing", e);
		// }

	// }

	/**
	 * get the transport string.
	 * 
	 * @return "tcp" in this case.
	 */
	public String getTransport() {
		return this.messageProcessor.transport;
	}

	/**
	 * Send message to whoever is connected to us. Uses the topmost via address
	 * to send to.
	 * 
	 * @param msg
	 *            is the message to send.
	 * @param isClient
	 */
	protected void sendMessage(byte[] msg, boolean isClient) throws IOException {

		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("sendMessage isClient  = " + isClient + " this = " + this);
		}
		lastActivityTimeStamp = System.currentTimeMillis();
		
		// NIOHandler nioHandler = ((NioTcpMessageProcessor) messageProcessor).nioHandler;
		// if(this.socketChannel != null && this.socketChannel.isConnected() && this.socketChannel.isOpen()) {
		// 	nioHandler.putSocket(NIOHandler.makeKey(this.peerAddress, this.peerPort), this.socketChannel);
		// }
		sendTCPMessage(msg, this.peerAddress, this.peerPort, isClient);
	}
	
	/**
	 * Send a message to a specified address.
	 * 
	 * @param message
	 *            Pre-formatted message to send.
	 * @param receiverAddress
	 *            Address to send it to.
	 * @param receiverPort
	 *            Receiver port.
	 * @throws IOException
	 *             If there is a problem connecting or sending.
	 */
	public void sendMessage(byte message[], InetAddress receiverAddress,
			int receiverPort, boolean retry) throws IOException {
		sendTCPMessage(message, receiverAddress, receiverPort, retry);
	}
	/**
	 * Send a message to a specified address.
	 * 
	 * @param message
	 *            Pre-formatted message to send.
	 * @param receiverAddress
	 *            Address to send it to.
	 * @param receiverPort
	 *            Receiver port.
	 * @throws IOException
	 *             If there is a problem connecting or sending.
	 */
	public void sendTCPMessage(byte message[], InetAddress receiverAddress,
			int receiverPort, boolean retry) throws IOException {
		// if (message == null || receiverAddress == null) {
		// 	logger.logError("receiverAddress = " + receiverAddress);
		// 	throw new IllegalArgumentException("Null argument");
		// }
		// lastActivityTimeStamp = System.currentTimeMillis();

		// if (peerPortAdvertisedInHeaders <= 0) {
		// 	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
		// 		logger.logDebug("receiver port = " + receiverPort
		// 				+ " for this channel " + this + " key " + key);
		// 	}
		// 	if (receiverPort <= 0) {
		// 		// if port is 0 we assume the default port for TCP
		// 		this.peerPortAdvertisedInHeaders = 5060;
		// 	} else {
		// 		this.peerPortAdvertisedInHeaders = receiverPort;
		// 	}
		// 	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
		// 		logger.logDebug("2.Storing peerPortAdvertisedInHeaders = "
		// 				+ peerPortAdvertisedInHeaders + " for this channel "
		// 				+ this + " key " + key);
		// 	}
		// }
		// NIOHandler nioHandler = ((NioTcpMessageProcessor) messageProcessor).nioHandler;
		
		// SocketChannel sock = nioHandler.sendBytes(this.messageProcessor
		// 			.getIpAddress(), receiverAddress, receiverPort, this.messageProcessor.transport,
		// 			message, retry, this);

		// if (sock != socketChannel && sock != null) {
		// 	if (socketChannel != null) {
		// 		if (logger.isLoggingEnabled(LogWriter.TRACE_WARN)) {
		// 			logger
		// 					.logWarning("[2] Old socket different than new socket on channel "
		// 							+ key + socketChannel + " " + sock);
		// 			logger.logStackTrace();
		// 			logger.logWarning("Old socket local ip address "
		// 					+ socketChannel.socket().getLocalSocketAddress());
		// 			logger.logWarning("Old socket remote ip address "
		// 					+ socketChannel.socket().getRemoteSocketAddress());
		// 			logger.logWarning("New socket local ip address "
		// 					+ sock.socket().getLocalSocketAddress());
		// 			logger.logWarning("New socket remote ip address "
		// 					+ sock.socket().getRemoteSocketAddress());
		// 		}
		// 		close(false, false); // we can call socketChannel.close() directly but we better use the inherited method
				
		// 		socketChannel = sock;
		// 		nioHandler.putMessageChannel(socketChannel, this);
				
		// 		onNewSocket(message);
		// 	}
			
		// 	if (socketChannel != null) {
		// 		if (logger.isLoggingEnabled(LogWriter.TRACE_WARN)) {
		// 			logger
		// 			.logWarning("There was no exception for the retry mechanism so we keep going "
		// 					+ key);
		// 		}
		// 	}
		// 	socketChannel = sock;
		// }

	}

	public void onNewSocket(byte[] message) {

	}
	

	/**
	 * Exception processor for exceptions detected from the parser. (This is
	 * invoked by the parser when an error is detected).
	 * 
	 * @param sipMessage
	 *            -- the message that incurred the error.
	 * @param ex
	 *            -- parse exception detected by the parser.
	 * @param header
	 *            -- header that caused the error.
	 * @throws ParseException
	 *             Thrown if we want to reject the message.
	 */
	public void handleException(ParseException ex, SIPMessage sipMessage,
			Class<?> hdrClass, String header, String message)
			throws ParseException {
		// if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
        //     logger.logDebug("Parsing Exception: " , ex);
		// // Log the bad message for later reference.
		// if ((hdrClass != null)
		// 		&& (hdrClass.equals(From.class) || hdrClass.equals(To.class)
		// 				|| hdrClass.equals(CSeq.class)
		// 				|| hdrClass.equals(Via.class)
		// 				|| hdrClass.equals(CallID.class)
		// 				|| hdrClass.equals(ContentLength.class)
		// 				|| hdrClass.equals(RequestLine.class) || hdrClass
		// 				.equals(StatusLine.class))) {
		// 	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
		// 		logger.logDebug("Encountered Bad Message \n"
		// 				+ sipMessage.toString());
		// 	}

		// 	// JvB: send a 400 response for requests (except ACK)
		// 	// Currently only UDP, @todo also other transports
		// 	String msgString = sipMessage.toString();
		// 	if (!msgString.startsWith("SIP/") && !msgString.startsWith("ACK ")) {
		// 		if (socketChannel != null) {
		// 			if (logger.isLoggingEnabled(LogWriter.TRACE_ERROR)) {
		// 				logger
		// 						.logError("Malformed mandatory headers: closing socket! :"
		// 								+ socketChannel.toString());
		// 			}

		// 			try {
		// 				socketChannel.close();

		// 			} catch (IOException ie) {
		// 				if (logger.isLoggingEnabled(LogWriter.TRACE_ERROR)) {
		// 					logger.logError("Exception while closing socket! :"
		// 							+ socketChannel.toString() + ":" + ie.toString());
		// 				}

		// 			}
		// 		}
		// 	}

		// 	throw ex;
		// } else {
		// 	sipMessage.addUnparsed(header);
		// }
	}

	/**
	 * Equals predicate.
	 * 
	 * @param other
	 *            is the other object to compare ourselves to for equals
	 */

	public boolean equals(Object other) {

		if (!this.getClass().equals(other.getClass()))
			return false;
		else {
			NettyTCPMessageChannel that = (NettyTCPMessageChannel) other;
			// if (this.socketChannel != that.socketChannel)
			// 	return false;
			// else
				return true;
		}
	}

	/**
	 * TCP Is not a secure protocol.
	 */
	public boolean isSecure() {
		return false;
	}
	
	public long getLastActivityTimestamp() {
		return lastActivityTimeStamp;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'close'");
	}

	@Override
	public SIPTransactionStack getSIPStack() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getSIPStack'");
	}

	@Override
	public boolean isReliable() {
		return true;
	}

	@Override
	public void sendMessage(SIPMessage sipMessage) throws IOException {
		System.out.println("NettyTCPMessageChannel::sendMessage");
	}

	@Override
	public String getPeerAddress() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getPeerAddress'");
	}

	@Override
	protected InetAddress getPeerInetAddress() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getPeerInetAddress'");
	}

	@Override
	protected String getPeerProtocol() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getPeerProtocol'");
	}

	@Override
	public int getPeerPort() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getPeerPort'");
	}

	@Override
	public int getPeerPacketSourcePort() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getPeerPacketSourcePort'");
	}

	@Override
	public InetAddress getPeerPacketSourceAddress() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getPeerPacketSourceAddress'");
	}

	@Override
	public String getKey() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getKey'");
	}

	@Override
	public String getViaHost() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getViaHost'");
	}

	@Override
	public int getViaPort() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getViaPort'");
	}

}
