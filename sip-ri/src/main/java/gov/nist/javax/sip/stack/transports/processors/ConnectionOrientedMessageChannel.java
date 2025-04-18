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
package gov.nist.javax.sip.stack.transports.processors;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.text.ParseException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

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
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.RetryAfter;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.Pipeline;
import gov.nist.javax.sip.parser.PipelinedMsgParser;
import gov.nist.javax.sip.parser.SIPMessageListener;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.ServerLog;
import gov.nist.javax.sip.stack.ServerRequestInterface;
import gov.nist.javax.sip.stack.ServerResponseInterface;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import gov.nist.javax.sip.stack.transports.processors.nio.NioTcpMessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.oio.TCPMessageProcessor;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public abstract class ConnectionOrientedMessageChannel extends MessageChannel implements
	SIPMessageListener, Runnable, RawMessageChannel {
	
	private static StackLogger logger = CommonLogger.getLogger(ConnectionOrientedMessageChannel.class);
	protected SIPTransactionStack sipStack;
	
	protected Socket mySock;
	
	protected PipelinedMsgParser myParser;
	
	protected String key;
	
	protected InputStream myClientInputStream; // just to pass to thread.
	
    // Set here on initialization to avoid thread leak. See issue 266
	protected boolean isRunning = true;
	
	protected boolean isCached;

    protected Thread mythread;    

    protected String myAddress;

    protected int myPort;

    protected InetAddress peerAddress;
    
    // This is the port and adress that we will find in the headers of the messages from the peer
    protected int peerPortAdvertisedInHeaders = -1;
    protected String peerAddressAdvertisedInHeaders;
    
    protected int peerPort;    
    protected String peerProtocol;
	
	private SIPStackTimerTask pingKeepAliveTimeoutTask;
    private Semaphore keepAliveSemaphore;
    
    private long keepAliveTimeout;    
    
    public ConnectionOrientedMessageChannel(SIPTransactionStack sipStack) {
    	this.sipStack = sipStack;
    	this.keepAliveTimeout = sipStack.getReliableConnectionKeepAliveTimeout();
    	if(keepAliveTimeout > 0) {
    		keepAliveSemaphore = new Semaphore(1);
    	}
	}
    
    /**
     * Returns "true" as this is a reliable transport.
     */
    public boolean isReliable() {
        return true;
    }
    
    /**
     * Close the message channel.
     */
    public void close() {
    	close(true, true);
    }
    
    protected abstract void close(boolean removeSocket, boolean stopKeepAliveTask);
    
	/**
     * Get my SIP Stack.
     *
     * @return The SIP Stack for this message channel.
     */
    public SIPTransactionStack getSIPStack() {
        return sipStack;
    }
    
    /**
     * get the address of the client that sent the data to us.
     *
     * @return Address of the client that sent us data that resulted in this
     *         channel being created.
     */
    public String getPeerAddress() {
        if (peerAddress != null) {
            return peerAddress.getHostAddress();
        } else
            return getHost();
    }

    public InetAddress getPeerInetAddress() {
        return peerAddress;
    }

    public String getPeerProtocol() {
        return this.peerProtocol;
    }
    
    /**
     * Return a formatted message to the client. We try to re-connect with the
     * peer on the other end if possible.
     *
     * @param sipMessage
     *            Message to send.
     * @throws IOException
     *             If there is an error sending the message
     */
    public void sendMessage(final SIPMessage sipMessage) throws IOException {

        if ( logger.isLoggingEnabled(LogWriter.TRACE_DEBUG) && !sipMessage.isNullRequest() ) {
            logger.logDebug("sendMessage:: " + sipMessage.getFirstLine() + " cseq method = " + sipMessage.getCSeq().getMethod());
        }
        //check for self routing
        MessageProcessor messageProcessor = sipStack.findMessageProcessor(this.getPeerAddress(), this.getPeerPort(), this.getPeerProtocol());
        if(messageProcessor != null) {
            sipStack.selfRouteMessage(this, sipMessage);
            return;            
        }        

        byte[] msg = sipMessage.encodeAsBytes(this.getTransport());

        long time = System.currentTimeMillis();
        
        // need to store the peerPortAdvertisedInHeaders in case the response has an rport (ephemeral) that failed to retry on the regular via port
        // for responses, no need to store anything for subsequent requests.
        if(peerPortAdvertisedInHeaders <= 0) {
        	if(sipMessage instanceof SIPResponse) {
        		SIPResponse sipResponse = (SIPResponse) sipMessage; 
        		Via via = sipResponse.getTopmostVia();
        		if(via.getRPort() > 0) {
	            	if(via.getPort() <=0) {    
	            		// if port is 0 we assume the default port for TCP
	            		this.peerPortAdvertisedInHeaders = 5060;
	            	} else {
	            		this.peerPortAdvertisedInHeaders = via.getPort();
	            	}
	            	if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
	                	logger.logDebug("1.Storing peerPortAdvertisedInHeaders = " + peerPortAdvertisedInHeaders + " for via port = " + via.getPort() + " via rport = " + via.getRPort() + " and peer port = " + peerPort + " for this channel " + this + " key " + key);
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

	protected abstract void sendMessage(byte[] msg, boolean b) throws IOException;
	
	public void processMessage(SIPMessage sipMessage, InetAddress address) {
        this.peerAddress = address;
        try {
            processMessage(sipMessage);
        } catch (Exception e) {
            if (logger.isLoggingEnabled(
                    ServerLog.TRACE_ERROR)) {
                logger.logError(
                        "ERROR processing self routing", e);
            }
        }
    }
	
	 /**
     * Gets invoked by the parser as a callback on successful message parsing
     * (i.e. no parser errors).
     *
     * @param sipMessage
     *            Message to process (this calls the application for processing
     *            the message).
     *
     *            Jvb: note that this code is identical to TCPMessageChannel,
     *            refactor some day
     */
    public void processMessage(SIPMessage sipMessage) throws Exception {
        try {
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
        	
            sipMessage.setRemoteAddress(this.peerAddress);
            sipMessage.setRemotePort(this.getPeerPort());
            sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
            sipMessage.setLocalPort(this.getPort());
            //Issue 3: https://telestax.atlassian.net/browse/JSIP-3
            sipMessage.setPeerPacketSourceAddress(this.peerAddress);
            sipMessage.setPeerPacketSourcePort(this.peerPort);
            
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
                //if(peerPortAdvertisedInHeaders <= 0) {
                	int hopPort = v.getPort();
                	if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    	logger.logDebug("hop port = " + hopPort + " for request " + sipMessage + " for this channel " + this + " key " + key);
                    }                	
                	if(hopPort <= 0) {    
                		// if port is 0 we assume the default port for TCP
                		this.peerPortAdvertisedInHeaders = 5060;
                	} else {
                		this.peerPortAdvertisedInHeaders = hopPort;
                	}
                	if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    	logger.logDebug("3.Storing peerPortAdvertisedInHeaders = " + peerPortAdvertisedInHeaders + " for this channel " + this + " key " + key);
                    }
                //}
                // may be needed to reconnect, when diff than peer address
                if(peerAddressAdvertisedInHeaders == null) {
                	peerAddressAdvertisedInHeaders = hop.getHost();
                	if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    	logger.logDebug("3.Storing peerAddressAdvertisedInHeaders = " + peerAddressAdvertisedInHeaders + " for this channel " + this + " key " + key);
                    }
                }
                
              if(!sipStack.isPatchReceivedRport())  
              {
                try {
                	if (mySock != null) { // selfrouting makes socket = null
                        				 // https://jain-sip.dev.java.net/issues/show_bug.cgi?id=297
                		this.peerAddress = mySock.getInetAddress();
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
                	if(sipStack.isPatchRport())
						if (!hasRPort && v.getPort() != peerPort) {
							// https://github.com/RestComm/jain-sip/issues/79
							if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
								logger.logDebug(
								"setting rport since viaPort " + v.getPort() + " different than peerPacketSourcePort "
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
              }
              else
              {
            	  if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            		  logger.logDebug("We did not use recived and rport");
              }
                // Use this for outgoing messages as well.
                if (!this.isCached && mySock != null) { // self routing makes
									                    // mySock=null
									                    // https://jain-sip.dev.java.net/issues/show_bug.cgi?id=297
                	this.isCached = true;
                    int remotePort = ((java.net.InetSocketAddress) mySock
                            .getRemoteSocketAddress()).getPort();
                    String key = IOHandler.makeKey(mySock.getInetAddress(),
                            remotePort);
                    if(this.messageProcessor instanceof NioTcpMessageProcessor) {
                    	// https://java.net/jira/browse/JSIP-475 don't use iohandler in case of NIO communications of the socket will leak in the iohandler sockettable
                    	((NioTcpMessageProcessor)this.messageProcessor).getNioHandler().putSocket(key, mySock.getChannel());
                    } else {
                    	((TCPMessageProcessor)this.messageProcessor).getIOHandler().putSocket(key, mySock);
                    }
                    // since it can close the socket it needs to be after the mySock usage otherwise
                    // it the socket will be disconnected and NPE will be thrown in some edge cases
                    ((ConnectionOrientedMessageProcessor)this.messageProcessor).cacheMessageChannel(this);
                }
            }

            // Foreach part of the request header, fetch it and process it

            long receptionTime = System.currentTimeMillis();
            //

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
                ServerRequestInterface sipServerRequest = sipStack
                        .newSIPServerRequest(sipRequest, messageProcessor.getListeningPoint().getProvider(), this);
                
                if (sipServerRequest != null) {
                    // try {
                        sipServerRequest.processRequest(sipRequest, this);
                    // } finally {
                    //     if (sipServerRequest instanceof SIPTransaction) {
                    //         SIPServerTransaction sipServerTx = (SIPServerTransaction) sipServerRequest;
                    //         if (!sipServerTx.passToListener())
                    //             ((SIPTransaction) sipServerRequest)
                    //                     .releaseSem();
                    //     }
                    // }
                } else {
                	if(sipStack.sipMessageValves.size() == 0) { // Allow message valves to nullify messages without error
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
                    //     if (sipServerResponse instanceof SIPTransaction
                    //             && !((SIPTransaction) sipServerResponse)
                    //                     .passToListener()) {
                    //         // Note that the semaphore is released in event
                    //         // scanner if the
                    //         // request is actually processed by the Listener.
                    //         ((SIPTransaction) sipServerResponse).releaseSem();
                    //     }
                    // }
                } else {
                	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                        ConnectionOrientedMessageChannel.logger.logDebug("null sipServerResponse as could not acquire semaphore or the valve dropped the message.");
                }
            }
        } finally {
        }
    }
    
    
  

    /**
     * This gets invoked when thread.start is called from the constructor.
     * Implements a message loop - reading the tcp connection and processing
     * messages until we are done or the other end has closed.
     */
    public void run() {
        Pipeline hispipe = null;
        // Create a pipeline to connect to our message parser.
        hispipe = new Pipeline(myClientInputStream, sipStack.getReadTimeout(),
                ((SIPTransactionStack) sipStack).getTimer());
        // Create a pipelined message parser to read and parse
        // messages that we write out to him.
        myParser = new PipelinedMsgParser(sipStack, this, hispipe,
                this.sipStack.getMaxMessageSize());
        // Start running the parser thread.
        myParser.processInput();
        // bug fix by Emmanuel Proulx
        int bufferSize = 4096;
        ((ConnectionOrientedMessageProcessor)this.messageProcessor).useCount++;
        this.isRunning = true;
        try {
            while (true) {
                try {
                    byte[] msg = new byte[bufferSize];
                    int nbytes = myClientInputStream.read(msg, 0, bufferSize);
                    // no more bytes to read...
                    if (nbytes == -1) {
                        hispipe.write("\r\n\r\n".getBytes("UTF-8"));
                        try {
                            if (sipStack.getMaxConnections() != -1) {
                                synchronized (messageProcessor) {
                                	((ConnectionOrientedMessageProcessor)this.messageProcessor).nConnections--;
                                	messageProcessor.notify();
                                }
                            }
                            hispipe.close();
                            close();
                        } catch (IOException ioex) {
                        }
                        return;
                    }                    
                    
                    hispipe.write(msg, 0, nbytes);

                } catch (IOException ex) {
                    // Terminate the message.
                    try {
                        hispipe.write("\r\n\r\n".getBytes("UTF-8"));
                    } catch (Exception e) {
                        // InternalErrorHandler.handleException(e);
                    }

                    try {
                        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                            logger.logDebug(
                                    "IO issue while closing socket " + ex.getMessage());
                        try {
                            if (sipStack.getMaxConnections() != -1) {
                                synchronized (messageProcessor) {
                                	((ConnectionOrientedMessageProcessor)this.messageProcessor).nConnections--;
                                	messageProcessor.notify();
                                }
                            }
                            close();
                            hispipe.close();
                        } catch (IOException ioex) {
                        }
                    } catch (Exception ex1) {
                        // Do nothing.
                    }
                    return;
                } catch (Exception ex) {
                    InternalErrorHandler.handleException(ex, logger);
                }
            }
        } finally {
            this.isRunning = false;
            ((ConnectionOrientedMessageProcessor)this.messageProcessor).remove(this);
            ((ConnectionOrientedMessageProcessor)this.messageProcessor).useCount--;
            // parser could be null if the socket was closed by the remote end already
            if(myParser != null) {
            	myParser.close();
            }
        }

    }

    
    public void uncache() {
        if (isCached && !isRunning) {
        	((ConnectionOrientedMessageProcessor)this.messageProcessor).remove(this);
        }
    }
    
    public void cache() {
        isCached = true;
    }
    /**
     * Get an identifying key. This key is used to cache the connection and
     * re-use it if necessary.
     */
    public String getKey() {
        if (this.key != null) {
            return this.key;
        } else {
            this.key = MessageChannel.getKey(this.peerAddress, this.peerPort,
                    getTransport());
            return this.key;
        }
    }

    /**
     * Get the host to assign to outgoing messages.
     *
     * @return the host to assign to the via header.
     */
    public String getViaHost() {
        return myAddress;
    }

    /**
     * Get the port for outgoing messages sent from the channel.
     *
     * @return the port to assign to the via header.
     */
    public int getViaPort() {
        return myPort;
    }

    /**
     * Get the port of the peer to whom we are sending messages.
     *
     * @return the peer port.
     */
    public int getPeerPort() {
        return peerPort;
    }

    public void setPeerPort(int peerPort) {
        this.peerPort = peerPort;
    }

    public int getPeerPacketSourcePort() {
        return this.peerPort;
    }

    public InetAddress getPeerPacketSourceAddress() {
        return this.peerAddress;
    }
    
    public InputStream getClientInputStream() {
        return myClientInputStream;
    }

    public void setClientInputStream(InputStream myClientInputStream) {
        this.myClientInputStream = myClientInputStream;
    }

	/*
     * (non-Javadoc)
     * @see gov.nist.javax.sip.parser.SIPMessageListener#sendSingleCLRF()
     */
	public void sendSingleCLRF() throws Exception {

		if(mySock != null && !mySock.isClosed()) {
			sendMessage("\r\n".getBytes("UTF-8"), false);
		}

        synchronized (this) {
            if (isRunning) {
            	if(keepAliveTimeout > 0) {
            		rescheduleKeepAliveTimeout(keepAliveTimeout);
            	}             
            }
        }
    }

    public void cancelPingKeepAliveTimeoutTaskIfStarted() {
    	if (pingKeepAliveTimeoutTask != null) {			 
			// && pingKeepAliveTimeoutTask.getSipTimerTask() != null
    		try {
				keepAliveSemaphore.acquire();
			} catch (InterruptedException e) {
				logger.logError("Couldn't acquire keepAliveSemaphore");
				return;
			}
	    	try {
	    		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
	                logger.logDebug("~~~ cancelPingKeepAliveTimeoutTaskIfStarted for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
	                        +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + ")");
	            }
	    		sipStack.getTimer().cancel(pingKeepAliveTimeoutTask);
	    	} finally {
	    		keepAliveSemaphore.release();
	    	}
    	}
    }

    public void setKeepAliveTimeout(long keepAliveTimeout) {
        if (keepAliveTimeout < 0){
            cancelPingKeepAliveTimeoutTaskIfStarted();
        }
        if (keepAliveTimeout == 0){
            keepAliveTimeout = messageProcessor.getSIPStack().getReliableConnectionKeepAliveTimeout();
        }

        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("~~~ setKeepAliveTimeout for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
                    +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + ")");
        }

        this.keepAliveTimeout = keepAliveTimeout;
        if(keepAliveSemaphore == null) {
        	keepAliveSemaphore = new Semaphore(1);
        }

        boolean isKeepAliveTimeoutTaskScheduled = pingKeepAliveTimeoutTask != null;
        if (isKeepAliveTimeoutTaskScheduled && keepAliveTimeout > 0){
            rescheduleKeepAliveTimeout(keepAliveTimeout);
        }
    }
    
    public long getKeepAliveTimeout() {
    	return keepAliveTimeout;
    }

    public void rescheduleKeepAliveTimeout(long newKeepAliveTimeout) {
//        long now = System.currentTimeMillis();
//        long lastKeepAliveReceivedTimeOrNow = lastKeepAliveReceivedTime == 0 ? now : lastKeepAliveReceivedTime;
//
//        long newScheduledTime =  lastKeepAliveReceivedTimeOrNow + newKeepAliveTimeout;

        StringBuilder methodLog = new StringBuilder();

        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            methodLog.append("~~~ rescheduleKeepAliveTimeout for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
                    +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + "): newKeepAliveTimeout=");
            if (newKeepAliveTimeout == Long.MAX_VALUE) {
                methodLog.append("Long.MAX_VALUE");
            } else {
                methodLog.append(newKeepAliveTimeout);
            }
//            methodLog.append(", lastKeepAliveReceivedTimeOrNow=");
//            methodLog.append(lastKeepAliveReceivedTimeOrNow);
//            methodLog.append(", newScheduledTime=");
//            methodLog.append(newScheduledTime);
        }

//        long delay = newScheduledTime > now ? newScheduledTime - now : 1;
        try {
			keepAliveSemaphore.acquire();
		} catch (InterruptedException e) {
			logger.logWarning("Couldn't acquire keepAliveSemaphore");
			return;
		}
		try{
	        if(pingKeepAliveTimeoutTask == null) {
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
	                logger.logDebug("~~~ cancelPingKeepAliveTimeout for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
	                        +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + ")");
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
		} finally {
        	keepAliveSemaphore.release();
        }
    }
    class KeepAliveTimeoutTimerTask extends SIPStackTimerTask {
    	KeepAliveTimeoutTimerTask () { 
    		super(KeepAliveTimeoutTimerTask.class.getSimpleName());
    	}
        @Override
        public String getId() {
            return ConnectionOrientedMessageChannel.this.toString();
        } 
        
        public void runTask() {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug(
                        "~~~ Starting processing of KeepAliveTimeoutEvent( " + peerAddress.getHostAddress() + "," + peerPort + ")...");
            }
            close(true, true);
            if(sipStack instanceof SipStackImpl) {
	            for (Iterator<SipProviderImpl> it = ((SipStackImpl)sipStack).getSipProviders(); it.hasNext();) {
	                SipProviderImpl nextProvider = (SipProviderImpl) it.next();
	                SipListener sipListener= nextProvider.getSipListener();
	                ListeningPoint[] listeningPoints = nextProvider.getListeningPoints();
	                for(ListeningPoint listeningPoint : listeningPoints) {
		            	if(sipListener!= null && sipListener instanceof SipListenerExt
		            			// making sure that we don't notify each listening point but only the one on which the timeout happened  
		            			&& listeningPoint.getIPAddress().equalsIgnoreCase(myAddress) && listeningPoint.getPort() == myPort && 
		            				listeningPoint.getTransport().equalsIgnoreCase(getTransport())) {
		            		((SipListenerExt)sipListener).processIOException(new IOExceptionEventExt(null, nextProvider, Reason.KeepAliveTimeout, myAddress, myPort,
		            				peerAddress.getHostAddress(), peerPort, getTransport()));
		                }
	                }
	            }  
            } else {
	            SipListener sipListener = sipStack.getSipListener();	            
	            if(sipListener instanceof SipListenerExt) {
	            	((SipListenerExt)sipListener).processIOException(new IOExceptionEventExt(null, this, Reason.KeepAliveTimeout, myAddress, myPort,
	                    peerAddress.getHostAddress(), peerPort, getTransport()));
	            }
            }
        }
    }
}
