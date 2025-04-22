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
package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.net.InetAddress;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.sip.Dialog;
import javax.sip.TransactionState;
import javax.sip.address.SipURI;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.IOExceptionEventExt;
import gov.nist.javax.sip.IOExceptionEventExt.Reason;
import gov.nist.javax.sip.ReleaseReferencesStrategy;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.RawMessageChannel;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyStreamMessageChannel;
import gov.nist.javax.sip.stack.transports.processors.nio.NioTlsMessageChannel;
import gov.nist.javax.sip.stack.transports.processors.oio.TLSMessageChannel;

/*
 * Modifications for TLS Support added by Daniel J. Martinez Manzano
 * <dani@dif.um.es> Bug fixes by Jeroen van Bemmel (JvB) and others.
 */

/**
 * Abstract class to support both client and server transactions. Provides an
 * encapsulation of a message channel, handles timer events, and creation of the
 * Via header for a message.
 *
 * @author Jeff Keyser
 * @author M. Ranganathan
 *
 *
 * @version 1.2 $Revision: 1.100 $ $Date: 2010-12-02 22:04:13 $
 */
public abstract class SIPTransactionImpl implements SIPTransaction {
	private static final long serialVersionUID = 1L;

	private static StackLogger logger = CommonLogger.getLogger(SIPTransaction.class);

	// Contribution on http://java.net/jira/browse/JSIP-417 from Alexander Saveliev
	private static final Pattern EXTRACT_CN = Pattern.compile(".*CN\\s*=\\s*([\\w*\\.\\-_]+).*");

    protected boolean toListener; // Flag to indicate that the listener gets

    // to see the event.

    protected int baseTimerInterval = SIPTransactionStack.BASE_TIMER_INTERVAL;
    /**
     * 5 sec Maximum duration a message will remain in the network
     */
    protected int T4 = 5000 / baseTimerInterval;

    /**
     * The maximum retransmit interval for non-INVITE requests and INVITE
     * responses
     */
    protected int T2 = 4000 / baseTimerInterval;
    protected int timerI = T4;

    protected int timerK = T4;

    protected int timerD = 32000 / baseTimerInterval;

    // Proposed feature for next release.
    private static final String DEFAULT_APP_DATA = "appdata.default";
    protected transient ConcurrentMap<String, Object> applicationData;

    protected SIPResponse lastResponse;

    // private SIPDialog dialog;

    protected boolean isMapped;

    // private transient TransactionSemaphore semaphore;

    // protected boolean eventPending; // indicate that an event is pending
    // here.

    protected String transactionId; // Transaction Id.

    // Audit tag used by the SIP Stack audit
    protected long auditTag = 0;

    // Parent stack for this transaction
    protected transient SIPTransactionStack sipStack;
    protected transient SipProviderImpl sipProvider;
    
    // Original request that is being handled by this transaction
    protected SIPRequest originalRequest;
    //jeand we nullify the originalRequest fast to save on mem and help GC
    // so we keep only those data instead
    protected byte[] originalRequestBytes;
    protected long originalRequestCSeqNumber;
    protected String originalRequestCallId;
    protected String originalRequestBranch;
    protected boolean originalRequestHasPort;


    // Underlying channel being used to send messages for this transaction
    protected transient MessageChannel encapsulatedChannel;

    protected transient SIPStackTimerTask timeoutTimer;
    protected transient SIPStackTimerTask retransmissionTimer;
    protected transient boolean timeoutTimerEnabled;
    protected AtomicBoolean timeoutTimerStarted = new AtomicBoolean(false);
    protected AtomicBoolean retransmissionTimerStarted = new AtomicBoolean(false);

    // Transaction branch ID
    protected String branch;

    // Method of the Request used to create the transaction.
    protected String method;

    // Current transaction state
    protected int currentState = -1;

    // Counter for caching of connections.
    // Connection lingers for collectionTime
    // after the Transaction goes to terminated state.
    protected int collectionTime;

    protected boolean terminatedEventDelivered;

    // aggressive flag to optimize eagerly
    protected ReleaseReferencesStrategy releaseReferencesStrategy;

    // caching fork id
    protected String forkId = null;
    protected String mergeId = null;

    // protected ExpiresTimerTask expiresTimerTask;
	// http://java.net/jira/browse/JSIP-420
    private MaxTxLifeTimeListener maxTxLifeTimeListener;

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getBranchId()
     */
    @Override
    public String getBranchId() {
        return this.branch;
    }

    // [Issue 284] https://jain-sip.dev.java.net/issues/show_bug.cgi?id=284
    // JAIN SIP drops 200 OK due to race condition
    // Wrapper that uses a semaphore for non reentrant listener
    // and a lock for reetrant listener to avoid race conditions
    // when 2 responses 180/200 OK arrives at the same time
    // class TransactionSemaphore {
    //     Semaphore sem = null;
    //     ReentrantLock lock = null;

    //     public TransactionSemaphore() {
    //         if(((SipStackImpl)sipStack).isReEntrantListener()) {
    //             lock = new ReentrantLock();
    //         } else {
    //             sem = new Semaphore(1, true);
    //         }
    //     }

    //     public boolean acquire() {
    //         try {
    //             if(((SipStackImpl)sipStack).isReEntrantListener()) {
    //                 lock.lock();
    //             } else {
    //                 sem.acquire();
    //             }
    //             return true;
    //         } catch (Exception ex) {
    //             logger.logError("Unexpected exception acquiring sem",
    //                     ex);
    //             InternalErrorHandler.handleException(ex);
    //             return false;
    //         }
    //     }

    //     public boolean tryAcquire() {
    //         try {
    //             if(((SipStackImpl)sipStack).isReEntrantListener()) {
    //                 return lock.tryLock(sipStack.maxListenerResponseTime, TimeUnit.SECONDS);
    //             } else {
    //                 return sem.tryAcquire(sipStack.maxListenerResponseTime, TimeUnit.SECONDS);
    //             }
    //         } catch (Exception ex) {
    //             logger.logError("Unexpected exception trying acquiring sem",
    //                     ex);
    //             InternalErrorHandler.handleException(ex);
    //             return false;
    //         }
    //     }

    //     public void release() {
    //         try {
    //             if(((SipStackImpl)sipStack).isReEntrantListener()) {
    //                 if(lock.isHeldByCurrentThread()) {
    //                     lock.unlock();
    //                 }
    //             } else {
    //                 sem.release();
    //             }
    //         } catch (Exception ex) {
    //             logger.logError("Unexpected exception releasing sem",
    //                             ex);
    //         }
    //     }
    // }

    /**
     * The linger timer is used to remove the transaction from the transaction
     * table after it goes into terminated state. This allows connection caching
     * and also takes care of race conditins.
     *
     *
     */
    public class LingerTimer extends SIPStackTimerTask {

        public LingerTimer() {
        	super(LingerTimer.class.getSimpleName());
            SIPTransaction sipTransaction = SIPTransactionImpl.this;
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("LingerTimer : "
                        + sipTransaction.getTransactionId());
            }

        }

        public void runTask() {
            cleanUp();
        }

        @Override
        public String getId() {
            Request request = getRequest();
            if (request != null && request instanceof SIPRequest) {
                return ((SIPRequest)request).getCallIdHeader().getCallId();
            } else {
                return originalRequestCallId;
            }
        }
    }

    /**
     * http://java.net/jira/browse/JSIP-420
     * This timer task will terminate the transaction after a configurable time
     *
     */
    class MaxTxLifeTimeListener extends SIPStackTimerTask {

    	MaxTxLifeTimeListener() {
    		super(MaxTxLifeTimeListener.class.getSimpleName());
    	}
        SIPTransaction sipTransaction = SIPTransactionImpl.this;

        public void runTask() {
            try {
            	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug("Fired MaxTxLifeTimeListener for tx " +  sipTransaction + " , tx id "+ sipTransaction.getTransactionId() + " , state " + sipTransaction.getState());
            	}

        		raiseErrorEvent(SIPTransactionErrorEvent.TIMEOUT_ERROR);

        		SIPStackTimerTask myTimer = new LingerTimer();
        		if(sipStack.getConnectionLingerTimer() != 0) {
        			sipStack.getTimer().schedule(myTimer, sipStack.getConnectionLingerTimer() * 1000);
    	        } else {
    	        	myTimer.runTask();
    	        }
                maxTxLifeTimeListener = null;

            } catch (Exception ex) {
                logger.logError("unexpected exception", ex);
            }
        }

        @Override
        public String getId() {
            Request request = getRequest();
            if (request != null && request instanceof SIPRequest) {
                return ((SIPRequest)request).getCallIdHeader().getCallId();
            } else {
                return originalRequestCallId;
            }
        }
    }

    // May be required by the subclasses for in memory datagrid frameworks
    protected SIPTransactionImpl() {
    }

    /**
     * Transaction constructor.
     *
     * @param newParentStack
     *            Parent stack for this transaction.
     * @param newEncapsulatedChannel
     *            Underlying channel for this transaction.
     */
    protected SIPTransactionImpl(SIPTransactionStack newParentStack, SipProviderImpl newSipProvider,
            MessageChannel newEncapsulatedChannel) {

        sipStack = newParentStack;
        sipProvider = newSipProvider;
        // this.semaphore = new TransactionSemaphore();

        encapsulatedChannel = newEncapsulatedChannel;

        if (this.isReliable()) {
                encapsulatedChannel.increaseUseCount();
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                    logger
                            .logDebug("use count for encapsulated channel"
                                    + this
                                    + " "
                                    + encapsulatedChannel.getUseCount() );
        }

        this.currentState = -1;

        disableRetransmissionTimer();
        disableTimeoutTimer();
        
        releaseReferencesStrategy = sipStack.getReleaseReferencesStrategy();
        this.applicationData = new ConcurrentHashMap<String, Object>(10);
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#cleanUp()
     */
    @Override
    public abstract void cleanUp();

	/**
   * @see gov.nist.javax.sip.stack.SIPTransaction#setOriginalRequest(gov.nist.javax.sip.message.SIPRequest)
   */
    @Override
    public void setOriginalRequest(SIPRequest newOriginalRequest) {

        // Branch value of topmost Via header
        String newBranch;

        final String newTransactionId = newOriginalRequest.getTransactionId();
        if (this.originalRequest != null
                && (!this.originalRequest.getTransactionId().equals(
                        newTransactionId))) {
            sipStack.removeTransactionHash(this);
        }
        // This will be cleared later.

        this.originalRequest = newOriginalRequest;
        this.originalRequestCSeqNumber = newOriginalRequest.getCSeq().getSeqNumber();
        this.originalRequestCallId = newOriginalRequest.getCallIdHeader().getCallId();
        final Via topmostVia = newOriginalRequest.getTopmostVia();
        this.originalRequestBranch = topmostVia.getBranch();
        this.originalRequestHasPort = topmostVia.hasPort();
        int originalRequestViaPort = topmostVia.getPort();

        if ( originalRequestViaPort == -1 ) {
            if (topmostVia.getTransport().equalsIgnoreCase("TLS") ) {
                originalRequestViaPort = 5061;
            } else {
                originalRequestViaPort = 5060;
            }
        }

        // just cache the control information so the
        // original request can be released later.
        this.method = newOriginalRequest.getMethod();

        this.transactionId = newTransactionId;

        originalRequest.setTransaction(this);

        // If the message has an explicit branch value set,
        newBranch = topmostVia.getBranch();
        if (newBranch != null) {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug("Setting Branch id : " + newBranch);

            // Override the default branch with the one
            // set by the message
            setBranch(newBranch);

        } else {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug("Branch id is null - compute TID!"
                        + newOriginalRequest.encode());
            setBranch(newTransactionId);
        }
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getOriginalRequest()
     */
    @Override
    public SIPRequest getOriginalRequest() {
    	if(getReleaseReferencesStrategy() != ReleaseReferencesStrategy.None && originalRequest == null && originalRequestBytes != null) {
            if(logger.isLoggingEnabled(StackLogger.TRACE_WARN)) {
                logger.logWarning("reparsing original request " + originalRequestBytes + " since it was eagerly cleaned up, but beware this is not efficient with the aggressive flag set !");
            }
            try {
                originalRequest = (SIPRequest) sipStack.getMessageParserFactory().createMessageParser(sipStack).parseSIPMessage(originalRequestBytes, true, false, null);
//                originalRequestBytes = null;
            } catch (ParseException e) {
            	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            		logger.logDebug("message " + originalRequestBytes + " could not be reparsed !", e);
            	}
            }
        }
    	
    	return this.originalRequest;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getRequest()
     */
    @Override
    public Request getRequest() {
        return (Request) getOriginalRequest();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isDialogCreatingTransaction()
     */
    @Override
    public boolean isDialogCreatingTransaction() {
        return Boolean.valueOf(isInviteTransaction() || getMethod().equals(Request.SUBSCRIBE) || getMethod().equals(Request.REFER));
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isInviteTransaction()
     */
    @Override
    public boolean isInviteTransaction() {
        return Boolean.valueOf(getMethod().equals(Request.INVITE));
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isCancelTransaction()
     */
    @Override
    public boolean isCancelTransaction() {
        return getMethod().equals(Request.CANCEL);
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isByeTransaction()
     */
    @Override
    public boolean isByeTransaction() {
        return getMethod().equals(Request.BYE);
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getMessageChannel()
     */
    @Override
    public MessageChannel getMessageChannel() {
        return encapsulatedChannel;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setBranch(java.lang.String)
     */
    @Override
    public void setBranch(String newBranch) {
        branch = newBranch;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getBranch()
     */
    @Override
    public String getBranch() {
        if (this.branch == null) {
            this.branch = originalRequestBranch;
        }
        return branch;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getMethod()
     */
    @Override
    public String getMethod() {
        return this.method;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getCSeq()
     */
    @Override
    public long getCSeq() {
        return this.originalRequestCSeqNumber;
    }

    public String getCallId() {
        return this.originalRequestCallId;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setState(int)
     */
    @Override
    public void setState(int newState) {
        // PATCH submitted by sribeyron
        if (currentState == TransactionState._COMPLETED) {
            if (newState != TransactionState._TERMINATED
                    && newState != TransactionState._CONFIRMED)
                newState = TransactionState._COMPLETED;
        }
        if (currentState == TransactionState._CONFIRMED) {
            if (newState != TransactionState._TERMINATED)
                newState = TransactionState._CONFIRMED;
        }
        if (currentState != TransactionState._TERMINATED) {
            currentState = newState;
        }
        else
            newState = currentState;
        // END OF PATCH

        if(newState == TransactionState._COMPLETED) {
        	enableTimeoutTimer(TIMER_H); // timer H must be started around now
        }

        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("Transaction:setState " + newState
                    + " " + this + " branchID = " + this.getBranch()
                    + " isClient = " + (this instanceof SIPClientTransaction));
            logger.logStackTrace();
        }
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getInternalState()
     */
    @Override
    public int getInternalState() {
        return this.currentState;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getState()
     */
    @Override
    public TransactionState getState() {
    	if(currentState < 0) {
    		return null;
    	}
        return TransactionState.getObject(this.currentState);
    }

    /**
     * Enables retransmission timer events for this transaction to begin in one
     * tick.
     */
    protected void enableRetransmissionTimer() {
        enableRetransmissionTimer(1);
    }

    /**
     * Enables retransmission timer events for this transaction to begin after
     * the number of ticks passed to this routine.
     *
     * @param tickCount
     *            Number of ticks before the next retransmission timer event
     *            occurs.
     */
    protected void enableRetransmissionTimer(int tickCount) {
    	// For INVITE Client transactions, double interval each time
        if (isInviteTransaction() && (this instanceof SIPClientTransaction)) {
            
        } else {
            // non-INVITE transactions and 3xx-6xx responses are capped at T2
        	tickCount = Math.min(tickCount,
                    getTimerT2());
        }  

        if(retransmissionTimer!=null) {
        	sipStack.getTimer().cancel(retransmissionTimer);
        }
        
        retransmissionTimer = new SIPTransactionRetransmissionTimerTask(this, tickCount);
        sipStack.getTimer().schedule(retransmissionTimer, tickCount * getBaseTimerInterval());
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#disableRetransmissionTimer()
     */
    @Override
    public void disableRetransmissionTimer() {
    	if(retransmissionTimer!=null) {
        	sipStack.getTimer().cancel(retransmissionTimer);
        	retransmissionTimer = null;
        }
    }

    protected void setTimeoutTimerActive() {
    	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("settingTimeoutTimerActive " + this);

    	this.timeoutTimerEnabled = true;
    }
    
    /**
     * Enables a timeout event to occur for this transaction after the number of
     * ticks passed to this method.
     *
     * @param tickCount
     *            Number of ticks before this transaction times out.
     */
    protected void enableTimeoutTimer(int tickCount) {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("enableTimeoutTimer " + this
                    + " tickCount " + tickCount + ",timeoutTimerEnabled " + timeoutTimerEnabled);

        if(timeoutTimer!=null) {
        	getSIPStack().getTimer().cancel(timeoutTimer);        	
        }
        
        if(timeoutTimerEnabled) {
        	if(timeoutTimer==null)
        		timeoutTimer = getTimeoutTimer();
        		
        	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug("enableTimeoutTimer " + this
                        + " timeoutTimer null " + (timeoutTimer==null));
        	
        	if(timeoutTimer!=null)
        		getSIPStack().getTimer().schedule(timeoutTimer,tickCount*getBaseTimerInterval());
        }
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#disableTimeoutTimer()
     */
    @Override
    public void disableTimeoutTimer() {
    	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) logger.logDebug("disableTimeoutTimer " + this);
    	if(timeoutTimer!=null) {
        	getSIPStack().getTimer().cancel(timeoutTimer);
        	timeoutTimer = null;
        }
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isTerminated()
     */
    @Override
    public boolean isTerminated() {
        return currentState == TransactionState._TERMINATED;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getHost()
     */
    @Override
    public String getHost() {
        return encapsulatedChannel.getHost();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getKey()
     */
    @Override
    public String getKey() {
        return encapsulatedChannel.getKey();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getPort()
     */
    @Override
    public int getPort() {
        return encapsulatedChannel.getPort();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getSIPStack()
     */
    @Override
    public SIPTransactionStack getSIPStack() {
        return (SIPTransactionStack) sipStack;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getPeerAddress()
     */
    @Override
    public String getPeerAddress() {
        return this.encapsulatedChannel.getPeerAddress();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getPeerPort()
     */
    @Override
    public int getPeerPort() {
        return this.encapsulatedChannel.getPeerPort();
    }

    // @@@ hagai
    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getPeerPacketSourcePort()
     */
    @Override
    public int getPeerPacketSourcePort() {
        return this.encapsulatedChannel.getPeerPacketSourcePort();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getPeerPacketSourceAddress()
     */
    @Override
    public InetAddress getPeerPacketSourceAddress() {
        return this.encapsulatedChannel.getPeerPacketSourceAddress();
    }

    public InetAddress getPeerInetAddress() {
        return this.encapsulatedChannel.getPeerInetAddress();
    }

    public String getPeerProtocol() {
        return this.encapsulatedChannel.getPeerProtocol();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTransport()
     */
    @Override
    public String getTransport() {
        return encapsulatedChannel.getTransport();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isReliable()
     */
    @Override
    public boolean isReliable() {
        return encapsulatedChannel.isReliable();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getViaHeader()
     */
    @Override
    public Via getViaHeader() {
        // Via header of the encapulated channel
        Via channelViaHeader;

        // Add the branch parameter to the underlying
        // channel's Via header
        channelViaHeader = encapsulatedChannel.getViaHeader();
        try {
            channelViaHeader.setBranch(branch);
        } catch (java.text.ParseException ex) {
        }
        return channelViaHeader;

    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#sendMessage(gov.nist.javax.sip.message.SIPMessage)
     */
    @Override
    public void sendMessage(final SIPMessage messageToSend) throws IOException, MessageTooLongException {
        // Use the peer address, port and transport
        // that was specified when the transaction was
        // created. Bug was noted by Bruce Evangelder
        // soleo communications.
    	this.setTimeoutTimerActive();
    	final RawMessageChannel channel = (RawMessageChannel) encapsulatedChannel;
        //check for self routing
        MessageProcessor messageProcessor = sipStack.findMessageProcessor(this.getPeerAddress(), this.getPeerPort(), this.getPeerProtocol());
        if(messageProcessor != null) {
            sipStack.selfRouteMessage(channel, messageToSend);                
        } else {
            encapsulatedChannel.sendMessage(messageToSend,
                this.getPeerInetAddress(), this.getPeerPort());
        } 
    }    

    /**
     * Parse the byte array as a message, process it through the transaction,
     * and send it to the SIP peer. This is just a placeholder method -- calling
     * it will result in an IO exception.
     *
     * @param messageBytes
     *            Bytes of the message to send.
     * @param receiverAddress
     *            Address of the target peer.
     * @param receiverPort
     *            Network port of the target peer.
     *
     * @throws IOException
     *             If called.
     */
    public void sendMessage(byte[] messageBytes,
            InetAddress receiverAddress, int receiverPort, boolean retry)
            throws IOException {
        throw new IOException(
                "Cannot send unparsed message through Transaction Channel!");
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#raiseErrorEvent(int)
     */
    @Override
    public void raiseErrorEvent(int errorEventID) {

        // Error event to send to all listeners
        SIPTransactionErrorEvent newErrorEvent;
        // Create the error event
        newErrorEvent = new SIPTransactionErrorEvent(this, errorEventID);

        sipStack.transactionErrorEvent(newErrorEvent);
        sipProvider.transactionErrorEvent(newErrorEvent);
        // }
        // Clear the event listeners after propagating the error.
        // Retransmit notifications are just an alert to the
        // application (they are not an error).
        if (errorEventID != SIPTransactionErrorEvent.TIMEOUT_RETRANSMIT) {
            // Errors always terminate a transaction
            this.setState(TransactionState._TERMINATED);

            if (this instanceof SIPServerTransaction && this.isByeTransaction()
                    && this.getDialog() != null)
                ((SIPDialog) this.getDialog())
                        .setState(SIPDialog.TERMINATED_STATE);
        }
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isServerTransaction()
     */
    @Override
    public boolean isServerTransaction() {
        return this instanceof SIPServerTransaction;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getDialog()
     */
    @Override
    public abstract Dialog getDialog();

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setDialog(gov.nist.javax.sip.stack.SIPDialog, java.lang.String)
     */
    @Override
    public abstract void setDialog(SIPDialog sipDialog, String dialogId);

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getRetransmitTimer()
     */
    @Override
    public int getRetransmitTimer() {
        return SIPTransactionStack.BASE_TIMER_INTERVAL;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getViaHost()
     */
    @Override
    public String getViaHost() {
        return this.getViaHeader().getHost();

    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getLastResponse()
     */
    @Override
    public SIPResponse getLastResponse() {
        return this.lastResponse;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getResponse()
     */
    @Override
    public Response getResponse() {
        return (Response) this.lastResponse;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTransactionId()
     */
    @Override
    public String getTransactionId() {
        return this.transactionId;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#hashCode()
     */
    @Override
    public int hashCode() {
        if (this.transactionId == null)
            return -1;
        else
            return this.transactionId.hashCode();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getViaPort()
     */
    @Override
    public int getViaPort() {
        return this.getViaHeader().getPort();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#doesCancelMatchTransaction(gov.nist.javax.sip.message.SIPRequest)
     */
    @Override
    public boolean doesCancelMatchTransaction(SIPRequest requestToTest) {

        // List of Via headers in the message to test
//        ViaList viaHeaders;
        // Topmost Via header in the list
        Via topViaHeader;
        // Branch code in the topmost Via header
        String messageBranch;
        // Flags whether the select message is part of this transaction
        boolean transactionMatches;

        transactionMatches = false;
        final SIPRequest origRequest = getOriginalRequest();
        if (origRequest == null
                || this.getMethod().equals(Request.CANCEL))
            return false;
        // Get the topmost Via header and its branch parameter
        topViaHeader = requestToTest.getTopmostVia();
        if (topViaHeader != null) {

//            topViaHeader = (Via) viaHeaders.getFirst();
            messageBranch = topViaHeader.getBranch();
            
            // If a new branch parameter exists,
            if (messageBranch != null && this.getBranch() != null) {

                // If the branch equals the branch in
                // this message,
                if (getBranch().equalsIgnoreCase(messageBranch)
                        && topViaHeader.getSentBy().equals(
                                origRequest.getTopmostVia().getSentBy())) {
                    transactionMatches = true;
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                        logger.logDebug("returning  true");
                }

            }
        }

        return transactionMatches;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setRetransmitTimer(int)
     */
    @Override
    public void setRetransmitTimer(int retransmitTimer) {

        if (retransmitTimer <= 0)
            throw new IllegalArgumentException(
                    "Retransmit timer must be positive!");
        if (this.retransmissionTimerStarted.get() || this.timeoutTimerStarted.get())
            throw new IllegalStateException(
                    "Transaction timer is already started");
        baseTimerInterval = retransmitTimer;
        // Commented out for Issue 303 since those timers are configured separately now
//      T4 = 5000 / BASE_TIMER_INTERVAL;
//      T2 = 4000 / BASE_TIMER_INTERVAL;
//      TIMER_D = 32000 / BASE_TIMER_INTERVAL;

    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#close()
     */
    @Override
    public void close() {
        this.encapsulatedChannel.close();
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("Closing " + this.encapsulatedChannel);

    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isSecure()
     */
    @Override
    public boolean isSecure() {
        return encapsulatedChannel.isSecure();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getMessageProcessor()
     */
    @Override
    public MessageProcessor getMessageProcessor() {
        return this.encapsulatedChannel.getMessageProcessor();
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setApplicationData(java.lang.Object)
     */

    @Override
    public void setApplicationData(Object data) {
        if (applicationData != null) {
            if (data == null) {
                this.applicationData.remove(DEFAULT_APP_DATA);
            } else {
                this.applicationData.put(DEFAULT_APP_DATA, data);
            }
        }
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getApplicationData()
     */
    @Override
    public Object getApplicationData() {
        return this.applicationData.get(DEFAULT_APP_DATA);
    }

    @Override
    public Object setApplicationData(String key, Object value) {
        return this.applicationData.put(key, value);
    }

    @Override
    public Object getApplicationData(String key) {
        return this.applicationData.get(key);
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setEncapsulatedChannel(gov.nist.javax.sip.stack.transports.processors.MessageChannel)
     */
    @Override
    public void setEncapsulatedChannel(MessageChannel messageChannel) {
        this.encapsulatedChannel = messageChannel;
        if ( this instanceof SIPClientTransaction ) {
        	this.encapsulatedChannel.setEncapsulatedClientTransaction((SIPClientTransaction) this);
        }
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getSipProvider()
     */
    @Override
    public SipProviderImpl getSipProvider() {
        return sipProvider;
    }

    /**
     * This realizes basic requirement form RFC 17.2.4 and 8.1.3.1
     * @see gov.nist.javax.sip.stack.SIPTransaction#raiseIOExceptionEvent(Reason reason)
     */
    @Override
    public void raiseIOExceptionEvent(Message message, Reason reason) {
        setState(TransactionState._TERMINATED);        
        // if (expiresTimerTask != null) {
        //     sipStack.getTimer().cancel(expiresTimerTask);
        // }
        String host = getPeerAddress();
        int port = getPeerPort();
        String transport = getTransport();
        IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(
        		message, this, reason, getHost(), getPort(), host, port, transport);
        getSipProvider().handleEvent(exceptionEvent, this);
    }

    // /**
    //  * @see gov.nist.javax.sip.stack.SIPTransaction#acquireSem()
    //  */
    // @Override
    // public boolean acquireSem() {
    //     // boolean retval = false;
    //     // if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    //     //     logger.logDebug("acquireSem [[[[" + this);
    //     //     logger.logStackTrace();
    //     // }
    //     // if ( this.sipStack.maxListenerResponseTime == -1 ) {
    //     //     retval = this.semaphore.acquire();
    //     // } else {
    //     //     retval = this.semaphore.tryAcquire();
    //     // }
    //     // if ( logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
    //     //     logger.logDebug(
    //     //         "acquireSem() returning : " + retval);
    //     // return retval;
    //     return true;
    // }


    // /**
    //  * @see gov.nist.javax.sip.stack.SIPTransaction#releaseSem()
    //  */
    // @Override
    // public void releaseSem() {
    //     // try {

    //     //     this.toListener = false;
    //     //     if ( logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
    //     //         logger.logDebug(
    //     //             "releaseSem() released this transaction sem : " + this);
    //     //     this.semRelease();            

    //     // } catch (Exception ex) {
    //     //     logger.logError("Unexpected exception releasing sem",
    //     //             ex);

    //     // }

    // }

    // public void semRelease() {
    //     // if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
    //     //     logger.logDebug("semRelease ]]]]" + this);
    //     //     logger.logStackTrace();
    //     // }
    //     // this.semaphore.release();
    // }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#passToListener()
     */

    @Override
    public boolean passToListener() {
        return toListener;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setPassToListener()
     */
    @Override
    public void setPassToListener() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("setPassToListener()");
        }
        this.toListener = true;

    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#testAndSetTransactionTerminatedEvent()
     */
    @Override
    public boolean testAndSetTransactionTerminatedEvent() {
        boolean retval = !this.terminatedEventDelivered;
        this.terminatedEventDelivered = true;
        return retval;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getCipherSuite()
     */
    @Override
    public String getCipherSuite() throws UnsupportedOperationException {
        if (this.getMessageChannel() instanceof TLSMessageChannel ) {
            if (  ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else if ( ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getHandshakeCompletedEvent() == null)
                return null;
            else return ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getHandshakeCompletedEvent().getCipherSuite();
        } // Added for https://java.net/jira/browse/JSIP-483
        else if (this.getMessageChannel() instanceof NioTlsMessageChannel) {
            if (  ((NioTlsMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else return ((NioTlsMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getCipherSuite();
        } else if(this.getMessageChannel() instanceof NettyStreamMessageChannel) {
        	if (  ((NettyStreamMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else return ((NettyStreamMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getCipherSuite();
        } else throw new UnsupportedOperationException("Not a TLS channel");

    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getLocalCertificates()
     */
    @Override
    public java.security.cert.Certificate[] getLocalCertificates() throws UnsupportedOperationException {
         if (this.getMessageChannel() instanceof TLSMessageChannel ) {
            if (  ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else if ( ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getHandshakeCompletedEvent() == null)
                return null;
            else return ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getHandshakeCompletedEvent().getLocalCertificates();
        } // Added for https://java.net/jira/browse/JSIP-483
        else if (this.getMessageChannel() instanceof NioTlsMessageChannel) {
            if (  ((NioTlsMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else return ((NioTlsMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getLocalCertificates();
        } else if(this.getMessageChannel() instanceof NettyStreamMessageChannel) {
        	if (  ((NettyStreamMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else return ((NettyStreamMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getLocalCertificates();
        } else throw new UnsupportedOperationException("Not a TLS channel");
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getPeerCertificates()
     */
    @Override
    public java.security.cert.Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        if (this.getMessageChannel() instanceof TLSMessageChannel ) {
            if (  ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else if ( ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getHandshakeCompletedEvent() == null)
                return null;
            else return ((TLSMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getHandshakeCompletedEvent().getPeerCertificates();
        } // Added for https://java.net/jira/browse/JSIP-483
        else if(this.getMessageChannel() instanceof NioTlsMessageChannel) {
        	if (  ((NioTlsMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else return ((NioTlsMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getPeerCertificates();
        } else if(this.getMessageChannel() instanceof NettyStreamMessageChannel) {
        	if (  ((NettyStreamMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener() == null )
                return null;
            else return ((NettyStreamMessageChannel) this.getMessageChannel()).getHandshakeCompletedListener().getPeerCertificates();
        } else throw new UnsupportedOperationException("Not a TLS channel");

    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#extractCertIdentities()
     */
    @Override
    public List<String> extractCertIdentities() throws SSLPeerUnverifiedException {
        if (this.getMessageChannel() instanceof TLSMessageChannel || 
                this.getMessageChannel() instanceof NioTlsMessageChannel || 
                this.getMessageChannel() instanceof NettyStreamMessageChannel) {
            
            List<String> certIdentities = new ArrayList<String>();
            Certificate[] certs = getPeerCertificates();
            if (certs == null) {
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug("No certificates available");
                }
                return certIdentities;
            }
            for (Certificate cert : certs) {
                X509Certificate x509cert = (X509Certificate) cert;
                Collection<List< ? >> subjAltNames = null;
                try {
                    subjAltNames = x509cert.getSubjectAlternativeNames();
                } catch (CertificateParsingException ex) {
                    if (logger.isLoggingEnabled()) {
                        logger.logError("Error parsing TLS certificate", ex);
                    }
                }
                // subjAltName types are defined in rfc2459
                final Integer dnsNameType = 2;
                final Integer uriNameType = 6;
                if (subjAltNames != null) {
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("found subjAltNames: " + subjAltNames);
                    }
                    // First look for a URI in the subjectAltName field
                    for (List< ? > altName : subjAltNames) {
                        // 0th position is the alt name type
                        // 1st position is the alt name data
                        if (altName.get(0).equals(uriNameType)) {
                            SipURI altNameUri;
                            try {
                                altNameUri = new AddressFactoryImpl().createSipURI((String) altName.get(1));
                                // only sip URIs are allowed
                                if(!"sip".equals(altNameUri.getScheme()))
                                    continue;
                                // user certificates are not allowed
                                if(altNameUri.getUser() != null)
                                    continue;
                                String altHostName = altNameUri.getHost();
                                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                                    logger.logDebug(
                                        "found uri " + altName.get(1) + ", hostName " + altHostName);
                                }
                                certIdentities.add(altHostName);
                            } catch (ParseException e) {
                                if (logger.isLoggingEnabled()) {
                                    logger.logError(
                                        "certificate contains invalid uri: " + altName.get(1));
                                }
                            }
                        }

                    }
                    // DNS An implementation MUST accept a domain name system
                    // identifier as a SIP domain identity if and only if no other
                    // identity is found that matches the "sip" URI type described
                    // above.
                    if (certIdentities.isEmpty()) {
                        for (List< ? > altName : subjAltNames) {
                            if (altName.get(0).equals(dnsNameType)) {
                                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                                    logger.logDebug("found dns " + altName.get(1));
                                }
                                certIdentities.add(altName.get(1).toString());
                            }
                        }
                    }
                } else {
                    // If and only if the subjectAltName does not appear in the
                    // certificate, the implementation MAY examine the CN field of the
                    // certificate. If a valid DNS name is found there, the
                    // implementation MAY accept this value as a SIP domain identity.
                    String dname = x509cert.getSubjectDN().getName();
                    String cname = "";
                    try {
                        Matcher matcher = EXTRACT_CN.matcher(dname);
                        if (matcher.matches()) {
                            cname = matcher.group(1);
                            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                                logger.logDebug("found CN: " + cname + " from DN: " + dname);
                            }
                            certIdentities.add(cname);
                        }
                    } catch (Exception ex) {
                        if (logger.isLoggingEnabled()) {
                            logger.logError("exception while extracting CN", ex);
                        }
                    }
                }
            }
            return certIdentities;
        } else
            throw new UnsupportedOperationException("Not a TLS channel");
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#isMessagePartOfTransaction(gov.nist.javax.sip.message.SIPMessage)
     */
    @Override
    public abstract boolean isMessagePartOfTransaction(SIPMessage messageToTest);

    @Override
    public ReleaseReferencesStrategy getReleaseReferencesStrategy() {
        return releaseReferencesStrategy;
    }

    @Override
    public void setReleaseReferencesStrategy(ReleaseReferencesStrategy releaseReferencesStrategy) {
        this.releaseReferencesStrategy = releaseReferencesStrategy;
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTimerD()
     */
    @Override
    public int getTimerD() {
        return timerD;
    }
    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTimerT2()
     */
    @Override
    public int getTimerT2() {
        return T2;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTimerT4()
     */
    @Override
    public int getTimerT4() {
        return T4;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setTimerD(int)
     */
    @Override
    public void setTimerD(int interval) {
        if(interval < 32000) {
            throw new IllegalArgumentException("To be RFC 3261 compliant, the value of Timer D should be at least 32s");
        }
        timerD = interval / baseTimerInterval;
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setTimerT2(int)
     */
    @Override
    public void setTimerT2(int interval) {
        T2 = interval / baseTimerInterval;
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setTimerT4(int)
     */
    @Override
    public void setTimerT4(int interval) {
        T4 = interval / baseTimerInterval;
        timerI = T4;
        timerK = T4;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getBaseTimerInterval()
     */
    @Override
    public int getBaseTimerInterval() {
      return this.baseTimerInterval;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getT4()
     */
    @Override
    public int getT4() {
      return this.T4;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getT2()
     */
    @Override
    public int getT2() {
      return this.T2;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTimerI()
     */
    @Override
    public int getTimerI() {
      return this.timerI;
    }

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getTimerK()
     */
    @Override
    public int getTimerK() {
      return this.timerK;
    }


    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#setForkId(java.lang.String)
     */
    @Override
    public void setForkId(String forkId) {
        if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("setForkId: " + forkId + " on transaction " + this + " with txId " + this.getTransactionId() + " and state " + this.getState());
        }
		this.forkId = forkId;
	}

    /**
     * @see gov.nist.javax.sip.stack.SIPTransaction#getForkId()
     */
    @Override
    public String getForkId() {
		return forkId;
	}

  /**
   * @see gov.nist.javax.sip.stack.SIPTransaction#scheduleMaxTxLifeTimeTimer()
   */
  @Override
  public void scheduleMaxTxLifeTimeTimer() {
  	if (maxTxLifeTimeListener == null && this.getMethod().equalsIgnoreCase(Request.INVITE) && sipStack.getMaxTxLifetimeInvite() > 0) {
      	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
              logger.logDebug("Scheduling MaxTxLifeTimeListener for tx " +  this + " , tx id "+ this.getTransactionId() + " , state " + this.getState());
      	}
      	maxTxLifeTimeListener = new MaxTxLifeTimeListener();
          sipStack.getTimer().schedule(maxTxLifeTimeListener,
                  sipStack.getMaxTxLifetimeInvite() * 1000);
      }

      if (maxTxLifeTimeListener == null && !this.getMethod().equalsIgnoreCase(Request.INVITE) && sipStack.getMaxTxLifetimeNonInvite() > 0) {
      	if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
              logger.logDebug("Scheduling MaxTxLifeTimeListener for tx " +  this + " , tx id "+ this.getTransactionId() + " , state " + this.getState());
      	}
      	maxTxLifeTimeListener = new MaxTxLifeTimeListener();
          sipStack.getTimer().schedule(maxTxLifeTimeListener,
                  sipStack.getMaxTxLifetimeNonInvite() * 1000);
      }
  }

	/**
   * @see gov.nist.javax.sip.stack.SIPTransaction#cancelMaxTxLifeTimeTimer()
   */
	@Override
  public void cancelMaxTxLifeTimeTimer() {
		if(maxTxLifeTimeListener != null) {
			if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("Cancelling MaxTxLifeTimeListener for tx " +  this + " , tx id "+ this.getTransactionId() + " , state " + this.getState());
        	}
			sipStack.getTimer().cancel(maxTxLifeTimeListener);
			maxTxLifeTimeListener = null;
		}
	}

    protected void stopTimeoutTimer() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("stopping TransactionTimer : " + getTransactionId());
        }
        if (timeoutTimer != null) {
            try {
                sipStack.getTimer().cancel(timeoutTimer);
            } catch (IllegalStateException ex) {
                if (!sipStack.isAlive())
                    return;
            } finally {
            	timeoutTimer = null;
            }
        }
    }

	/**
   * @see gov.nist.javax.sip.stack.SIPTransaction#getMergeId()
   */
	@Override
  public String getMergeId() {
		if(mergeId == null) {
			return ((SIPRequest)getRequest()).getMergeId();
		}
		return mergeId;
	}

	/**
	 * @see gov.nist.javax.sip.stack.SIPTransaction#getAuditTag()
	 */
  @Override
  public long getAuditTag() {
    return auditTag;
  }

  /**
   * @see gov.nist.javax.sip.stack.SIPTransaction#setAuditTag(long)
   */
  @Override
  public void setAuditTag(long auditTag) {
    this.auditTag = auditTag;
  }

  /**
   * @see gov.nist.javax.sip.stack.SIPServerTransaction#isTransactionMapped()
   */
  @Override
  public boolean isTransactionMapped() {
      return this.isMapped;
  }

  /**
   * @see gov.nist.javax.sip.stack.SIPServerTransaction#setTransactionMapped(boolean)
   */
  @Override
  public void setTransactionMapped(boolean transactionMapped) {
    isMapped = transactionMapped;
  }

  /**
   * @see gov.nist.javax.sip.stack.SIPTransaction#setCollectionTime(int)
   */
  @Override
  public void setCollectionTime(int collectionTime) {
    this.collectionTime = collectionTime;
  }
}
