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
import java.util.Iterator;

import javax.net.ssl.HandshakeCompletedListener;
import javax.sip.ListeningPoint;
import javax.sip.SipListener;
import javax.sip.address.Hop;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.InternalErrorHandler;
import gov.nist.core.LogWriter;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.IOExceptionEventExt;
import gov.nist.javax.sip.IOExceptionEventExt.Reason;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.header.RetryAfter;
import gov.nist.javax.sip.header.StatusLine;
import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.SIPMessageListener;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.ServerRequestInterface;
import gov.nist.javax.sip.stack.ServerResponseInterface;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import gov.nist.javax.sip.stack.transports.processors.HandshakeCompletedListenerImpl;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.RawMessageChannel;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Netty Stream Based Transport Protocol (TCP, TLS, WSS, WS, SCTP...) Message
 * Channel to handle SIP Messages
 * 
 * @author Jean Deruelle
 */
public class NettyStreamMessageChannel extends MessageChannel implements
		SIPMessageListener, RawMessageChannel {
	private static StackLogger logger = CommonLogger
			.getLogger(NettyStreamMessageChannel.class);
	// private static final String SUBPROTOCOL = null;

	Bootstrap bootstrap;
	ChannelInitializer<? extends Channel> nettyChannelInitializer;
	NettyConnectionListener nettyConnectionListener;

	protected Channel channel;

	protected SIPTransactionStack sipStack;
	protected long lastActivityTimeStamp;

	protected String myAddress;

	protected int myPort;

	protected InetAddress peerAddress;

	// This is the port and adress that we will find in the headers of the messages
	// from the peer
	protected int peerPortAdvertisedInHeaders = -1;
	protected String peerAddressAdvertisedInHeaders;

	protected int peerPort;

	protected String peerProtocol;

	private boolean isCached;

	private SIPStackTimerTask pingKeepAliveTimeoutTask;
	private long keepAliveTimeout;

	// Added for https://java.net/jira/browse/JSIP-483
	protected HandshakeCompletedListenerImpl handshakeCompletedListener;
	protected boolean handshakeCompleted = false;
	private boolean isWebsocket;
	private boolean isSctp;

	protected NettyStreamMessageChannel(NettyStreamMessageProcessor nettyStreamMessageProcessor,
			Channel channel) {
		this(
				((InetSocketAddress) channel.remoteAddress()).getAddress(),
				((InetSocketAddress) channel.remoteAddress()).getPort(),
				nettyStreamMessageProcessor);
		this.channel = channel;
		this.messageProcessor = nettyStreamMessageProcessor;
		this.sipStack = nettyStreamMessageProcessor.getSIPStack();
		isWebsocket = ListeningPointExt.WS.equalsIgnoreCase(nettyStreamMessageProcessor.getTransport()) ||
				ListeningPointExt.WSS.equalsIgnoreCase(nettyStreamMessageProcessor.getTransport());

		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("Done creating NettyStreamMessageChannel " + this + " socketChannel = " + channel);
		}
	}

	public NettyStreamMessageChannel(
			InetAddress inetAddress,
			int port,
			NettyStreamMessageProcessor nettyStreamMessageProcessor) {

		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("NettyStreamMessageChannel: "
					+ inetAddress.getHostAddress() + ":" + port + "/" + nettyStreamMessageProcessor.getTransport());
		}

		messageProcessor = nettyStreamMessageProcessor;
		this.sipStack = messageProcessor.getSIPStack();

		isWebsocket = ListeningPointExt.WS.equalsIgnoreCase(nettyStreamMessageProcessor.getTransport()) ||
				ListeningPointExt.WSS.equalsIgnoreCase(nettyStreamMessageProcessor.getTransport());
		isSctp = ListeningPoint.SCTP.equalsIgnoreCase(nettyStreamMessageProcessor.getTransport());

		bootstrap = new Bootstrap();
		if (!isWebsocket) {

			if (isSctp) {
				nettyChannelInitializer = new NettySctpChannelInitializer(nettyStreamMessageProcessor);

				bootstrap = bootstrap.group(nettyStreamMessageProcessor.workerGroup)
						.channel(NioSctpChannel.class);

				bootstrap.option(SctpChannelOption.SCTP_NODELAY, sipStack.getSctpNodelay());
				bootstrap.option(SctpChannelOption.SCTP_DISABLE_FRAGMENTS, sipStack.getSctpDisableFragments());
				bootstrap.option(SctpChannelOption.SCTP_FRAGMENT_INTERLEAVE, sipStack.getSctpFragmentInterleave());
				// bootstrap.option(SctpChannelOption.SCTP_INIT_MAXSTREAMS,
				// sipStack.getSctpInitMaxStreams());
				bootstrap.option(SctpChannelOption.SO_SNDBUF, sipStack.getSctpSoSndbuf());
				bootstrap.option(SctpChannelOption.SO_RCVBUF, sipStack.getSctpSoRcvbuf());
				bootstrap.option(SctpChannelOption.SO_LINGER, sipStack.getSctpSoLinger());
			} else {
				nettyChannelInitializer = new NettyStreamChannelInitializer(nettyStreamMessageProcessor,
						nettyStreamMessageProcessor.sslClientContext);

				bootstrap = bootstrap.group(nettyStreamMessageProcessor.workerGroup)
						.channel(nioOrEpollSocketChannel());
				bootstrap = bootstrap.option(ChannelOption.SO_RCVBUF, sipStack.getReceiveUdpBufferSize());
				bootstrap = bootstrap.option(ChannelOption.SO_SNDBUF, sipStack.getSendUdpBufferSize());
				bootstrap = bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
			}

			bootstrap = bootstrap.handler(nettyChannelInitializer)
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.sipStack.getConnectionTimeout());
		} else if (isWebsocket) {
			nettyChannelInitializer = new NettyStreamChannelInitializer(nettyStreamMessageProcessor,
					nettyStreamMessageProcessor.sslClientContext);

			bootstrap = bootstrap.group(nettyStreamMessageProcessor.workerGroup)
					.channel(nioOrEpollSocketChannel());

			String uri = getTransport().toLowerCase() + "://" + inetAddress.getHostAddress() + ":" + port
					+ "/websocket";
			logger.logInfo("websocket URI: " + uri);

			WebSocketDecoderConfig decoderConfig = WebSocketDecoderConfig.newBuilder()
					.expectMaskedFrames(false)
					.allowMaskMismatch(true)
					.allowExtensions(true)
					.maxFramePayloadLength(1048576)
					.build();

			// final WebSocketClientHandler handler =
			// new WebSocketClientHandler(
			// WebSocketClientHandshakerFactory.newHandshaker(
			// new URI(uri),
			// WebSocketVersion.V13,
			// SUBPROTOCOL,
			// true,
			// new DefaultHttpHeaders(),
			// decoderConfig.maxFramePayloadLength(),
			// true,
			// decoderConfig.allowMaskMismatch()));

			bootstrap.handler(new LoggingHandler(LogLevel.DEBUG))
					.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) {
							ChannelPipeline p = ch.pipeline();
							if (nettyStreamMessageProcessor.sslClientContext != null) {
								p.addLast(nettyStreamMessageProcessor.sslClientContext.newHandler(
										ch.alloc(),
										inetAddress.getHostAddress(),
										port));
							}
							p.addLast(new NettyWebSocketFrameDecoder(nettyStreamMessageProcessor, decoderConfig));
							// Encoder
							p.addLast(new WebSocket13FrameEncoder(false));
							// p.addLast(
							// new HttpClientCodec(),
							// new HttpObjectAggregator(65536),
							// // WebSocketClientCompressionHandler.INSTANCE,
							// handler);
							p.addLast(new NettyMessageHandler(nettyStreamMessageProcessor));

						}
					});
		}
		nettyConnectionListener = new NettyConnectionListener(this);

		this.peerAddress = inetAddress;
		this.peerPort = port;
		this.peerProtocol = nettyStreamMessageProcessor.getTransport();

		myAddress = nettyStreamMessageProcessor.getIpAddress().getHostAddress();
		myPort = nettyStreamMessageProcessor.getPort();

		keepAliveTimeout = sipStack.getReliableConnectionKeepAliveTimeout();
		if (nettyStreamMessageProcessor.sslClientContext != null && getHandshakeCompletedListener() == null) {
			HandshakeCompletedListenerImpl listner = new HandshakeCompletedListenerImpl(this);
			setHandshakeCompletedListener(listner);
		}
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("NettyStreamMessageChannel: Done creating NettyStreamMessageChannel "
					+ this + " socketChannel = " + channel);
		}
	}

	protected void close(boolean removeSocket, boolean stopKeepAliveTask) {
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("Closing NettyStreamMessageChannel "
					+ this + " socketChannel = " + channel);
		}
		if (channel != null && channel.isActive()) {
			channel.close();
		}
		// this.isRunning = false;
		if (removeSocket) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("Removing NettyStreamMessageChannel "
						+ this + " socketChannel = " + channel);
			}
			((NettyStreamMessageProcessor) messageProcessor).remove(this);
		}
		if (stopKeepAliveTask) {
			cancelPingKeepAliveTimeoutTaskIfStarted();
		}
	}

	/**
	 * get the transport string.
	 * 
	 * @return "tcp" in this case.
	 */
	public String getTransport() {
		return messageProcessor.getTransport();
	}

	/**
	 * Send message to whoever is connected to us. Uses the topmost via address
	 * to send to.
	 * 
	 * @param msg
	 *                 is the message to send.
	 * @param isClient
	 */
	protected void sendMessage(byte[] msg, boolean isClient) throws IOException {

		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("sendMessage isClient  = " + isClient + " this = " + this);
		}
		lastActivityTimeStamp = System.currentTimeMillis();

		// NIOHandler nioHandler = ((NioTcpMessageProcessor)
		// messageProcessor).nioHandler;
		// if(this.socketChannel != null && this.socketChannel.isConnected() &&
		// this.socketChannel.isOpen()) {
		// nioHandler.putSocket(NIOHandler.makeKey(this.peerAddress, this.peerPort),
		// this.socketChannel);
		// }
		sendTCPMessage(msg, this.peerAddress, this.peerPort, isClient);
	}

	/**
	 * Send a message to a specified address.
	 * 
	 * @param message
	 *                        Pre-formatted message to send.
	 * @param receiverAddress
	 *                        Address to send it to.
	 * @param receiverPort
	 *                        Receiver port.
	 * @throws IOException
	 *                     If there is a problem connecting or sending.
	 */
	@Override
	public void sendMessage(byte message[], InetAddress receiverAddress,
			int receiverPort, boolean retry) throws IOException {
		sendTCPMessage(message, receiverAddress, receiverPort, retry);
	}

	/**
	 * Send a message to a specified address.
	 * 
	 * @param message
	 *                        Pre-formatted message to send.
	 * @param receiverAddress
	 *                        Address to send it to.
	 * @param receiverPort
	 *                        Receiver port.
	 * @throws IOException
	 *                     If there is a problem connecting or sending.
	 */
	public void sendTCPMessage(byte message[], InetAddress receiverAddress,
			int receiverPort, boolean retry) throws IOException {
		if (message == null || receiverAddress == null) {
			logger.logError("receiverAddress = " + receiverAddress);
			throw new IllegalArgumentException("Null argument");
		}
		lastActivityTimeStamp = System.currentTimeMillis();

		if (peerPortAdvertisedInHeaders <= 0) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("receiver port = " + receiverPort
						+ " for this channel " + this + " key " + getKey());
			}
			if (receiverPort <= 0) {
				// if port is 0 we assume the default port for TCP
				this.peerPortAdvertisedInHeaders = 5060;
			} else {
				this.peerPortAdvertisedInHeaders = receiverPort;
			}
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("2.Storing peerPortAdvertisedInHeaders = "
						+ peerPortAdvertisedInHeaders + " for this channel "
						+ this + " key " + getKey());
			}
		}

		ByteBuf byteBuf = Unpooled.wrappedBuffer(message);
		if (channel == null || !channel.isActive()) {
			// Take a cached socket to the destination,
			// if none create a new one and cache it
			if (channel != null) {
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
					logger.logDebug(
							"Channel not active " + this.myAddress + ":" + this.myPort + ", trying to reconnect "
									+ this.peerAddress + ":" + this.peerPort + " for this channel "
									+ channel + " key " + getKey());
				}
				channel.close();
			}
			nettyConnectionListener.addPendingMessage(byteBuf);
			ChannelFuture channelFuture = bootstrap.connect(this.peerAddress, this.peerPort);
			channelFuture.addListener(nettyConnectionListener);
		} else {
			writeMessage(byteBuf);
		}
	}

	protected void writeMessage(ByteBuf message) throws IOException {
		// Commenting Blocking mode as it creates deadlock
		// when sync() is called from the listener from channelhandler
		// You MUST NOT call ChannelFuture.sync() in your ChannelHandler.
		// if(sipStack.nioMode.equals(NIOMode.BLOCKING)) {
		// try {
		// ChannelFuture future = channel.writeAndFlush(message).sync();
		// if (future.isSuccess() == false) {
		// throw new IOException("Failed to send message " + message.toString());
		// }
		// } catch (InterruptedException e) {
		// logger.logError("Failed to reconnect ", e);
		// throw new IOException(e);
		// }
		// } else {
		ChannelFuture future = null;
		if (!isWebsocket) {
			if (isSctp) {
				future = channel.writeAndFlush(new SctpMessage(0, 0, message));
			} else {
				future = channel.writeAndFlush(message);
			}
		} else {
			future = channel.writeAndFlush(new TextWebSocketFrame(message));
			// channel.pipeline().replace(HttpRequestEncoder.class, "ws-encoder", new
			// WebSocket13FrameEncoder(false));
		}
		final ChannelFuture finalFuture = future;
		final NettyStreamMessageChannel current = this;
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture completeFuture) {
				assert finalFuture == completeFuture;
				if (!finalFuture.isSuccess()) {
					finalFuture.cause().printStackTrace();
					if (sipStack.getMessageProcessorExecutor() != null) {
						sipStack.getMessageProcessorExecutor().addTaskLast(
								new NettyConnectionFailureThread(current, finalFuture));
					} else {
						current.triggerConnectFailure(finalFuture);
					}
				} else {
					logger.logDebug(channel.pipeline().toString());
				}
				// else {
				// finalFuture.channel().close();
				// }
			}
		});
		// }
	}

	/**
	 * Exception processor for exceptions detected from the parser. (This is
	 * invoked by the parser when an error is detected).
	 * 
	 * @param sipMessage
	 *                   -- the message that incurred the error.
	 * @param ex
	 *                   -- parse exception detected by the parser.
	 * @param header
	 *                   -- header that caused the error.
	 * @throws ParseException
	 *                        Thrown if we want to reject the message.
	 */
	public void handleException(ParseException ex, SIPMessage sipMessage,
			Class<?> hdrClass, String header, String message)
			throws ParseException {
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
			logger.logDebug("Parsing Exception: ", ex);
		// Log the bad message for later reference.
		if ((hdrClass != null)
				&& (hdrClass.equals(From.class) || hdrClass.equals(To.class)
						|| hdrClass.equals(CSeq.class)
						|| hdrClass.equals(Via.class)
						|| hdrClass.equals(CallID.class)
						|| hdrClass.equals(ContentLength.class)
						|| hdrClass.equals(RequestLine.class) || hdrClass
								.equals(StatusLine.class))) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("Encountered Bad Message \n"
						+ sipMessage.toString());
			}

			// JvB: send a 400 response for requests (except ACK)
			// Currently only UDP, @todo also other transports
			String msgString = sipMessage.toString();
			if (!msgString.startsWith("SIP/") && !msgString.startsWith("ACK ")) {
				if (channel != null) {
					if (logger.isLoggingEnabled(LogWriter.TRACE_ERROR)) {
						logger
								.logError("Malformed mandatory headers: closing socket! :"
										+ channel.toString());
					}

					close();
				}
			}

			throw ex;
		} else {
			sipMessage.addUnparsed(header);
		}
	}

	/**
	 * Equals predicate.
	 * 
	 * @param other
	 *              is the other object to compare ourselves to for equals
	 */

	public boolean equals(Object other) {

		if (!this.getClass().equals(other.getClass()))
			return false;
		else {
			NettyStreamMessageChannel that = (NettyStreamMessageChannel) other;
			if (this.channel != that.channel)
				return false;
			else
				return true;
		}
	}

	/**
	 * TCP Is not a secure protocol.
	 */
	public boolean isSecure() {
		return getTransport().equalsIgnoreCase(ListeningPoint.TLS);
	}

	public long getLastActivityTimestamp() {
		return lastActivityTimeStamp;
	}

	@Override
	/**
	 * Close the message channel.
	 */
	public void close() {
		close(true, true);
	}

	@Override
	public SIPTransactionStack getSIPStack() {
		return this.messageProcessor.getSIPStack();
	}

	@Override
	public boolean isReliable() {
		return true;
	}

	/**
	 * Actually proces the parsed message.
	 *
	 * @param sipMessage
	 * @throws Exception
	 */
	public void processMessage(SIPMessage sipMessage) throws Exception {
		if (sipMessage.getFrom() == null || sipMessage.getTo() == null
				|| sipMessage.getCallId() == null
				|| sipMessage.getCSeq() == null
				|| sipMessage.getViaHeaders() == null) {

			if (logger.isLoggingEnabled()) {
				String badmsg = sipMessage.encode();
				logger.logError("bad message " + badmsg);
				logger.logError(">>> Dropped Bad Msg");
			}
			return;
		}

		if (channel != null && channel.remoteAddress() != null) {
			InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
			sipMessage.setRemoteAddress(remoteAddress.getAddress());
			sipMessage.setRemotePort(remoteAddress.getPort());
			// Issue 3: https://telestax.atlassian.net/browse/JSIP-3
			if (logger.isLoggingEnabled(LogWriter.TRACE_INFO)) {
				logger.logInfo("Setting SIPMessage peerPacketSource to: " + remoteAddress.getAddress().getHostAddress()
						+ ":" + remoteAddress.getPort());
			}
			sipMessage.setPeerPacketSourceAddress(remoteAddress.getAddress());
			sipMessage.setPeerPacketSourcePort(remoteAddress.getPort());

		} else {
			sipMessage.setRemoteAddress(peerAddress);
			sipMessage.setRemotePort(peerPort);
			// Issue 3: https://telestax.atlassian.net/browse/JSIP-3
			if (logger.isLoggingEnabled(LogWriter.TRACE_INFO)) {
				logger.logInfo("Setting SIPMessage peerPacketSource to: " + peerAddress.getHostAddress()
						+ ":" + peerPort);
			}
			sipMessage.setPeerPacketSourceAddress(peerAddress);
			sipMessage.setPeerPacketSourcePort(peerPort);
		}

		sipMessage.setLocalPort(this.getPort());
		sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());

		ViaList viaList = sipMessage.getViaHeaders();
		// For a request
		// first via header tells where the message is coming from.
		// For response, this has already been recorded in the outgoing
		// message.
		if (sipMessage instanceof SIPRequest) {
			Via v = (Via) viaList.getFirst();
			// the peer address and tag it appropriately.
			Hop hop = sipStack.getAddressResolver().resolveAddress(v.getHop());
			this.peerProtocol = v.getTransport();
			// if(peerPortAdvertisedInHeaders <= 0) {
			int hopPort = v.getPort();
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("hop port = " + hopPort + " for request " + sipMessage + " for this channel " + this
						+ " key " + getKey());
			}
			if (hopPort <= 0) {
				// if port is 0 we assume the default port for TCP
				this.peerPortAdvertisedInHeaders = 5060;
			} else {
				this.peerPortAdvertisedInHeaders = hopPort;
			}
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("3.Storing peerPortAdvertisedInHeaders = " + peerPortAdvertisedInHeaders
						+ " for this channel " + this + " key " + getKey());
			}
			// }
			// may be needed to reconnect, when diff than peer address
			if (peerAddressAdvertisedInHeaders == null) {
				peerAddressAdvertisedInHeaders = hop.getHost();
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
					logger.logDebug("3.Storing peerAddressAdvertisedInHeaders = " + peerAddressAdvertisedInHeaders
							+ " for this channel " + this + " key " + getKey());
				}
			}

			if (!sipStack.isPatchReceivedRport()) {
				try {
					if (channel != null) { // selfrouting makes socket = null
											// https://jain-sip.dev.java.net/issues/show_bug.cgi?id=297
						this.peerAddress = ((InetSocketAddress) channel.remoteAddress()).getAddress();
					}
					// Check to see if the received parameter matches
					// the peer address and tag it appropriately.

					// JvB: dont do this. It is both costly and incorrect
					// Must set received also when it is a FQDN, regardless
					// whether
					// it resolves to the correct IP address
					// InetAddress sentByAddress =
					// InetAddress.getByName(hop.getHost());
					// JvB: if sender added 'rport', must always set received
					boolean hasRPort = v.hasParameter(Via.RPORT);
					if (sipStack.isPatchRport())
						if (!hasRPort && v.getPort() != peerPort) {
							// https://github.com/RestComm/jain-sip/issues/79
							if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
								logger.logDebug(
										"setting rport since viaPort " + v.getPort()
												+ " different than peerPacketSourcePort "
												+ peerPort + " so that the response can be routed back");
							}
							hasRPort = true;
						}
					if (hasRPort
							|| !hop.getHost().equals(
									this.peerAddress.getHostAddress())) {
						v.setParameter(Via.RECEIVED, this.peerAddress
								.getHostAddress());
					}
					// @@@ hagai
					// JvB: technically, may only do this when Via already
					// contains
					// rport
					v.setParameter(Via.RPORT, Integer.toString(this.peerPort));
				} catch (java.text.ParseException ex) {
					InternalErrorHandler.handleException(ex);
				}
			} else {
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
					logger.logDebug("We did not use recived and rport");
			}

			// Use this for outgoing messages as well.
			if (!this.isCached && channel != null) { // self routing makes
				// mySock=null
				// https://jain-sip.dev.java.net/issues/show_bug.cgi?id=297
				this.isCached = true;

				// since it can close the socket it needs to be after the mySock usage otherwise
				// it the socket will be disconnected and NPE will be thrown in some edge cases
				((NettyStreamMessageProcessor) this.messageProcessor).cacheMessageChannel(this);
			}
		}

		long receptionTime = System.currentTimeMillis();

		if (sipMessage instanceof SIPRequest) {
			// This is a request - process the request.
			SIPRequest sipRequest = (SIPRequest) sipMessage;
			// Create a new sever side request processor for this
			// message and let it handle the rest.

			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug(
						"----Processing Message---");
			}
			if (logger.isLoggingEnabled(
					ServerLogger.TRACE_MESSAGES)) {

				sipStack.getServerLogger().logMessage(sipMessage, this
						.getPeerHostPort().toString(),
						this.messageProcessor.getIpAddress()
								.getHostAddress()
								+ ":" + this.messageProcessor.getPort(),
						false, receptionTime);

			}
			// Check for reasonable size - reject message
			// if it is too long.
			if (sipStack.getMaxMessageSize() > 0
					&& sipRequest.getSize()
							+ (sipRequest.getContentLength() == null ? 0
									: sipRequest.getContentLength()
											.getContentLength()) > sipStack
													.getMaxMessageSize()) {
				SIPResponse sipResponse = sipRequest
						.createResponse(SIPResponse.MESSAGE_TOO_LARGE);
				byte[] resp = sipResponse
						.encodeAsBytes(this.getTransport());
				this.sendMessage(resp, false);
				throw new Exception("Message size exceeded");
			}

			String sipVersion = ((SIPRequest) sipMessage).getRequestLine()
					.getSipVersion();
			if (!sipVersion.equals("SIP/2.0")) {
				SIPResponse versionNotSupported = ((SIPRequest) sipMessage)
						.createResponse(Response.VERSION_NOT_SUPPORTED,
								"Bad SIP version " + sipVersion);
				this.sendMessage(versionNotSupported.encodeAsBytes(this
						.getTransport()), false);
				throw new Exception("Bad version ");
			}

			String method = ((SIPRequest) sipMessage).getMethod();
			String cseqMethod = ((SIPRequest) sipMessage).getCSeqHeader()
					.getMethod();

			if (!method.equalsIgnoreCase(cseqMethod)) {
				SIPResponse sipResponse = sipRequest
						.createResponse(SIPResponse.BAD_REQUEST);
				byte[] resp = sipResponse
						.encodeAsBytes(this.getTransport());
				this.sendMessage(resp, false);
				throw new Exception("Bad CSeq method" + sipMessage + " method " + method);
			}

			// Stack could not create a new server request interface.
			// maybe not enough resources.
			ServerRequestInterface sipServerRequest = sipStack.newSIPServerRequest(sipRequest, this);

			if (sipServerRequest != null) {
				// try {
				sipServerRequest.processRequest(sipRequest, this);
				// } finally {
				// if (sipServerRequest instanceof SIPTransaction) {
				// SIPServerTransaction sipServerTx = (SIPServerTransaction) sipServerRequest;
				// if (!sipServerTx.passToListener())
				// ((SIPTransaction) sipServerRequest)
				// .releaseSem();
				// }
				// }
			} else {
				if (sipStack.sipMessageValves.size() == 0) { // Allow message valves to nullify
																// messages without error
					SIPResponse response = sipRequest
							.createResponse(Response.SERVICE_UNAVAILABLE);

					RetryAfter retryAfter = new RetryAfter();

					// Be a good citizen and send a decent response code back.
					try {
						retryAfter.setRetryAfter((int) (10 * (Math.random())));
						response.setHeader(retryAfter);
						this.sendMessage(response);
					} catch (Exception e) {
						// IGNore
					}
					if (logger.isLoggingEnabled())
						logger
								.logWarning(
										"Dropping message -- could not acquire semaphore");
				}
			}
		} else {
			SIPResponse sipResponse = (SIPResponse) sipMessage;
			// JvB: dont do this
			// if (sipResponse.getStatusCode() == 100)
			// sipResponse.getTo().removeParameter("tag");
			try {
				sipResponse.checkHeaders();
			} catch (ParseException ex) {
				if (logger.isLoggingEnabled())
					logger.logError(
							"Dropping Badly formatted response message >>> "
									+ sipResponse);
				return;
			}
			// This is a response message - process it.
			// Check the size of the response.
			// If it is too large dump it silently.
			if (sipStack.getMaxMessageSize() > 0
					&& sipResponse.getSize()
							+ (sipResponse.getContentLength() == null ? 0
									: sipResponse.getContentLength()
											.getContentLength()) > sipStack
													.getMaxMessageSize()) {
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
					logger.logDebug(
							"Message size exceeded");
				return;

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
				// if (sipServerResponse instanceof SIPTransaction
				// && !((SIPTransaction) sipServerResponse)
				// .passToListener()) {
				// // Note that the semaphore is released in event
				// // scanner if the
				// // request is actually processed by the Listener.
				// ((SIPTransaction) sipServerResponse).releaseSem();
				// }
				// }
			} else {
				if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
					NettyStreamMessageChannel.logger.logDebug(
							"null sipServerResponse as could not acquire semaphore or the valve dropped the message.");
			}

		}
	}

	@Override
	public void sendMessage(SIPMessage sipMessage) throws IOException {
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG) && !sipMessage.isNullRequest()) {
			logger.logDebug("sendMessage:: " + sipMessage.getFirstLine() + " cseq method = "
					+ sipMessage.getCSeq().getMethod());
		}

		// check for self routing
		MessageProcessor messageProcessor = getSIPStack().findMessageProcessor(getPeerAddress(), getPeerPort(),
				getPeerProtocol());
		if (messageProcessor != null) {
			getSIPStack().selfRouteMessage(this, sipMessage);
			return;
		}

		byte[] msg = sipMessage.encodeAsBytes(this.getTransport());

		long time = System.currentTimeMillis();

		// need to store the peerPortAdvertisedInHeaders in case the response has an
		// rport (ephemeral) that failed to retry on the regular via port
		// for responses, no need to store anything for subsequent requests.
		if (peerPortAdvertisedInHeaders <= 0) {
			if (sipMessage instanceof SIPResponse) {
				SIPResponse sipResponse = (SIPResponse) sipMessage;
				Via via = sipResponse.getTopmostVia();
				if (via.getRPort() > 0) {
					if (via.getPort() <= 0) {
						// if port is 0 we assume the default port for TCP
						this.peerPortAdvertisedInHeaders = 5060;
					} else {
						this.peerPortAdvertisedInHeaders = via.getPort();
					}
					if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
						logger.logDebug("1.Storing peerPortAdvertisedInHeaders = " + peerPortAdvertisedInHeaders
								+ " for via port = " + via.getPort() + " via rport = " + via.getRPort()
								+ " and peer port = " + peerPort + " for this channel " + this + " key " + getKey());
					}
				}
			}
		}

		// JvB: also retry for responses, if the connection is gone we should
		// try to reconnect
		this.sendMessage(msg, sipMessage instanceof SIPRequest);

		// message was sent without any exception so let's set set port and
		// address before we feed it to the logger
		sipMessage.setRemoteAddress(this.peerAddress);
		sipMessage.setRemotePort(this.peerPort);
		sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
		sipMessage.setLocalPort(this.getPort());

		if (logger.isLoggingEnabled(
				ServerLogger.TRACE_MESSAGES))
			logMessage(sipMessage, peerAddress, peerPort, time);
	}

	@Override
	public String getPeerAddress() {
		return this.peerAddress.getHostAddress();
	}

	@Override
	public InetAddress getPeerInetAddress() {
		return this.peerAddress;
	}

	@Override
	public String getPeerProtocol() {
		return this.peerProtocol;
	}

	@Override
	public int getPeerPort() {
		return this.peerPort;
	}

	@Override
	public int getPeerPacketSourcePort() {
		return this.peerPort;
	}

	@Override
	public InetAddress getPeerPacketSourceAddress() {
		return this.peerAddress;
	}

	@Override
	public String getKey() {
		return MessageChannel.getKey(peerAddress, peerPort, getTransport());
	}

	@Override
	public String getViaHost() {
		return this.myAddress;
	}

	@Override
	public int getViaPort() {
		return this.myPort;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gov.nist.javax.sip.parser.SIPMessageListener#sendSingleCLRF()
	 */
	public void sendSingleCLRF() throws Exception {

		if (channel != null && channel.isActive()) {
			sendMessage("\r\n".getBytes("UTF-8"), false);
		}

		if (keepAliveTimeout > 0) {
			synchronized (this) {
				// if (isRunning) {
				rescheduleKeepAliveTimeout(keepAliveTimeout);
				// }
			}
		}
	}

	public void cancelPingKeepAliveTimeoutTaskIfStarted() {
		if (pingKeepAliveTimeoutTask != null) {
			// && pingKeepAliveTimeoutTask.getSipTimerTask() != null

			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("~~~ cancelPingKeepAliveTimeoutTaskIfStarted for MessageChannel(key=" + getKey()
						+ "), clientAddress=" + peerAddress
						+ ", clientPort=" + peerPort + ", timeout=" + keepAliveTimeout + ")");
			}
			sipStack.getTimer().cancel(pingKeepAliveTimeoutTask);

		}
	}

	public void setKeepAliveTimeout(long keepAliveTimeout) {
		if (keepAliveTimeout < 0) {
			cancelPingKeepAliveTimeoutTaskIfStarted();
		}
		if (keepAliveTimeout == 0) {
			keepAliveTimeout = messageProcessor.getSIPStack().getReliableConnectionKeepAliveTimeout();
		}

		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug(
					"~~~ setKeepAliveTimeout for MessageChannel(key=" + getKey() + "), clientAddress=" + peerAddress
							+ ", clientPort=" + peerPort + ", timeout=" + keepAliveTimeout + ")");
		}

		this.keepAliveTimeout = keepAliveTimeout;
		boolean isKeepAliveTimeoutTaskScheduled = pingKeepAliveTimeoutTask != null;
		if (isKeepAliveTimeoutTaskScheduled && keepAliveTimeout > 0) {
			rescheduleKeepAliveTimeout(keepAliveTimeout);
		}
	}

	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}

	public void rescheduleKeepAliveTimeout(long newKeepAliveTimeout) {
		// long now = System.currentTimeMillis();
		// long lastKeepAliveReceivedTimeOrNow = lastKeepAliveReceivedTime == 0 ? now :
		// lastKeepAliveReceivedTime;
		//
		// long newScheduledTime = lastKeepAliveReceivedTimeOrNow + newKeepAliveTimeout;

		StringBuilder methodLog = new StringBuilder();

		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			methodLog.append("~~~ rescheduleKeepAliveTimeout for MessageChannel(key=" + getKey() + "), clientAddress="
					+ peerAddress
					+ ", clientPort=" + peerPort + ", timeout=" + keepAliveTimeout + "): newKeepAliveTimeout=");
			if (newKeepAliveTimeout == Long.MAX_VALUE) {
				methodLog.append(Long.MAX_VALUE);
			} else {
				methodLog.append(newKeepAliveTimeout);
			}
			// methodLog.append(", lastKeepAliveReceivedTimeOrNow=");
			// methodLog.append(lastKeepAliveReceivedTimeOrNow);
			// methodLog.append(", newScheduledTime=");
			// methodLog.append(newScheduledTime);
		}

		// long delay = newScheduledTime > now ? newScheduledTime - now : 1;
		if (pingKeepAliveTimeoutTask == null) {
			pingKeepAliveTimeoutTask = new KeepAliveTimeoutTimerTask();
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				methodLog.append(", scheduling pingKeepAliveTimeoutTask to execute after ");
				methodLog.append(keepAliveTimeout / 1000);
				methodLog.append(" seconds");
				logger.logDebug(methodLog.toString());
			}
			sipStack.getTimer().schedule(pingKeepAliveTimeoutTask, keepAliveTimeout);
		} else {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("~~~ cancelPingKeepAliveTimeout for MessageChannel(key=" + getKey()
						+ "), clientAddress=" + peerAddress
						+ ", clientPort=" + peerPort + ", timeout=" + keepAliveTimeout + ")");
			}
			sipStack.getTimer().cancel(pingKeepAliveTimeoutTask);
			pingKeepAliveTimeoutTask = new KeepAliveTimeoutTimerTask();
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				methodLog.append(", scheduling pingKeepAliveTimeoutTask to execute after ");
				methodLog.append(keepAliveTimeout / 1000);
				methodLog.append(" seconds");
				logger.logDebug(methodLog.toString());
			}
			sipStack.getTimer().schedule(pingKeepAliveTimeoutTask, keepAliveTimeout);
		}
	}

	class KeepAliveTimeoutTimerTask extends SIPStackTimerTask {
		KeepAliveTimeoutTimerTask() {
			super(KeepAliveTimeoutTimerTask.class.getSimpleName());
		}

		@Override
		public String getId() {
			return channel.toString();
		}

		public void runTask() {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug(
						"~~~ Starting processing of KeepAliveTimeoutEvent( " + peerAddress.getHostAddress() + ","
								+ peerPort + ")...");
			}
			close(true, true);
			if (sipStack instanceof SipStackImpl) {
				for (Iterator<SipProviderImpl> it = ((SipStackImpl) sipStack).getSipProviders(); it.hasNext();) {
					SipProviderImpl nextProvider = (SipProviderImpl) it.next();
					SipListener sipListener = nextProvider.getSipListener();
					ListeningPoint[] listeningPoints = nextProvider.getListeningPoints();
					for (ListeningPoint listeningPoint : listeningPoints) {
						if (sipListener != null && sipListener instanceof SipListenerExt
						// making sure that we don't notify each listening point but only the one on
						// which the timeout happened
								&& listeningPoint.getIPAddress().equalsIgnoreCase(myAddress)
								&& listeningPoint.getPort() == myPort &&
								listeningPoint.getTransport().equalsIgnoreCase(getTransport())) {
							((SipListenerExt) sipListener).processIOException(
									new IOExceptionEventExt(nextProvider, Reason.KeepAliveTimeout, myAddress, myPort,
											peerAddress.getHostAddress(), peerPort, getTransport()));
						}
					}
				}
			} else {
				SipListener sipListener = sipStack.getSipListener();
				if (sipListener instanceof SipListenerExt) {
					((SipListenerExt) sipListener).processIOException(
							new IOExceptionEventExt(this, Reason.KeepAliveTimeout, myAddress, myPort,
									peerAddress.getHostAddress(), peerPort, getTransport()));
				}
			}
		}
	}

	// Methods below Added for https://java.net/jira/browse/JSIP-483
	public void setHandshakeCompletedListener(
			HandshakeCompletedListener handshakeCompletedListenerImpl) {
		this.handshakeCompletedListener = (HandshakeCompletedListenerImpl) handshakeCompletedListenerImpl;
	}

	/**
	 * @return the handshakeCompletedListener
	 */
	public HandshakeCompletedListenerImpl getHandshakeCompletedListener() {
		return (HandshakeCompletedListenerImpl) handshakeCompletedListener;
	}

	/**
	 * @return the handshakeCompleted
	 */
	public boolean isHandshakeCompleted() {
		return handshakeCompleted;
	}

	/**
	 * @param handshakeCompleted the handshakeCompleted to set
	 */
	public void setHandshakeCompleted(boolean handshakeCompleted) {
		this.handshakeCompleted = handshakeCompleted;
	}

	public void triggerConnectFailure(ChannelFuture channelFuture) {
		SIPTransaction transaction = getEncapsulatedClientTransaction();
		// alert of IOException to pending Data TXs
		if (transaction != null) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("triggerConnectFailure transaction:" + transaction);
				channelFuture.cause().printStackTrace();
			}
			if (transaction != null) {
				if (transaction instanceof SIPClientTransaction) {
					// 8.1.3.1 Transaction Layer Errors
					if (transaction.getRequest() != null &&
							!transaction.getRequest().getMethod().equalsIgnoreCase("ACK")) {
						SIPRequest req = (SIPRequest) transaction.getRequest();
						SIPResponse unavRes = req.createResponse(Response.SERVICE_UNAVAILABLE,
								"Transport error sending request.");
						try {
							this.processMessage(unavRes);
						} catch (Exception e) {
							if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
								logger.logDebug("failed to report transport error", e);
							}
						}
					}
				} else {
					// 17.2.4 Handling Transport Errors
					transaction.raiseIOExceptionEvent(Reason.ConnectionFailure);
				}
			}
		}
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("triggerConnectFailure close");
		}
		close(true, false);
	}

	public Class<? extends SocketChannel> nioOrEpollSocketChannel() {
		if (Epoll.isAvailable() && !isSctp) {
			return EpollSocketChannel.class;
		} else {
			return NioSocketChannel.class;
		}
	}
}
