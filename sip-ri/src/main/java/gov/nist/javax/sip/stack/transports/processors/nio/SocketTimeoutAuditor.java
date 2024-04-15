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
package gov.nist.javax.sip.stack.transports.processors.nio;

import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

/**
 * @author jean.deruelle@gmail.com
 * 
 */
public class SocketTimeoutAuditor extends SIPStackTimerTask {
	ExecutorService executorService = Executors.newSingleThreadExecutor();
	
	private static StackLogger logger = CommonLogger.getLogger(SocketTimeoutAuditor.class);
	long nioSocketMaxIdleTime;
	private NIOHandler nioHandler;
	
	public SocketTimeoutAuditor(long nioSocketMaxIdleTime, NIOHandler nioHandler) {
		super(SocketTimeoutAuditor.class.getSimpleName());
		this.nioSocketMaxIdleTime = nioSocketMaxIdleTime;
		this.nioHandler = nioHandler;
	}
        
	@Override
	public String getId() {
		return nioHandler.toString();
	}   	
	
	public void runTask() {
		int closedCount = 0;
		long startTime = System.currentTimeMillis();
		long endTime = 0;
		logger.logWarning("Start SocketTimeoutAuditor time : " + startTime + ". Channels map size : " + nioHandler.channelMap.size());
		try {
			// Reworked the method for https://java.net/jira/browse/JSIP-471
			if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
				logger.logDebug("keys to check for inactivity removal " + nioHandler.channelMap.keySet());
			}
			Iterator<Entry<SocketChannel, NioTcpMessageChannel>> entriesIterator = nioHandler.channelMap.entrySet().iterator();
			while(entriesIterator.hasNext()) {
				Entry<SocketChannel, NioTcpMessageChannel> entry = entriesIterator.next();
				SocketChannel socketChannel = entry.getKey();
				NioTcpMessageChannel messageChannel = entry.getValue();
				if(System.currentTimeMillis() - messageChannel.getLastActivityTimestamp() > nioSocketMaxIdleTime) {
					logger.logInfo("Remove socket : " + messageChannel.getKey());
					if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
						logger.logDebug("Will remove socket " + messageChannel.getKey() + " lastActivity="
								+ messageChannel.getLastActivityTimestamp() + " current= " +
								System.currentTimeMillis() + " socketChannel = "
								+ socketChannel);
					}
					
					Future<?> future = executorService.submit(new ChannelCloseRunnable(messageChannel));

					try {
					    future.get(5000, TimeUnit.MILLISECONDS);
					}
					catch(Exception ex) {
						logger.logError("Exception in SocketTimeoutAuditor : ", ex);
					}
					
					//removing anyway,otherwise we may get into indefinite loop
					nioHandler.removeMessageChannel(entry.getKey());
					entriesIterator = nioHandler.channelMap.entrySet().iterator();
					closedCount++;
				} else {
					if(logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
						logger.logDebug("don't remove socket " + messageChannel.getKey() + " as lastActivity="
								+ messageChannel.getLastActivityTimestamp() + " and current= " +
								System.currentTimeMillis() + " socketChannel = "
								+ socketChannel);
					}
				}
			}
		} catch (Exception anything) {
			logger.logError("Exception in SocketTimeoutAuditor : ", anything);
		}
		endTime = System.currentTimeMillis();
		logger.logWarning("End SocketTimeoutAuditor time : " + endTime + ". Closed channels number : " + closedCount + ". Duration : " + (endTime - startTime));
	}
	
	private class ChannelCloseRunnable implements Runnable {
		NioTcpMessageChannel messageChannel;
		
		public ChannelCloseRunnable(NioTcpMessageChannel messageChannel) {
			this.messageChannel = messageChannel;
		}
		
		public void run() {
			try {
				messageChannel.close();
			} catch (Exception anything) {
				logger.logError("Exception in SocketTimeoutAuditor : ", anything);
			}
			
		}
	}
}