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
package gov.nist.javax.sip.stack.timers;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import gov.nist.core.CommonLogger;
import gov.nist.core.NamingThreadFactory;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipStackImpl;

/**
 * Implementation of the SIP Timer based on java.util.concurrent.ScheduledThreadPoolExecutor
 * Seems to perform 
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class ScheduledExecutorSipTimer implements SipTimer {
	private static StackLogger logger = CommonLogger.getLogger(ScheduledExecutorSipTimer.class);
	protected SipStackImpl sipStackImpl;
	ScheduledThreadPoolExecutor threadPoolExecutor;
    
	public ScheduledExecutorSipTimer() {
		threadPoolExecutor = new ScheduledThreadPoolExecutor(1, new NamingThreadFactory("jain_sip_timer_executor"));		
	}
	
	private void schedulePurgeTaskIfNeeded() {
		int purgePeriod = Integer.parseInt(sipStackImpl.getConfigurationProperties().getProperty("gov.nist.javax.sip.timers.SCHEDULED_EXECUTOR_PURGE_DELAY", "1"));
		if(purgePeriod > 0) {
			Runnable r = new Runnable() {			
				public void run() {
					try {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("Purging canceled timer tasks...");
						}
						threadPoolExecutor.purge();
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("Purging canceled timer tasks completed.");
						}						
					}
					catch (Exception e) {
						logger.logError("failed to execute purge",e);
					}
				}
			};
			threadPoolExecutor.scheduleWithFixedDelay(r, purgePeriod, purgePeriod, TimeUnit.MINUTES);
		}
	}
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#stop()
	 */
	public void stop() {
		threadPoolExecutor.shutdown();
		logger.logStackTrace(StackLogger.TRACE_DEBUG);
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			logger.logInfo("the sip stack timer " + this.getClass().getName() + " has been stopped");
		}
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long)
	 */
	@Override
	public boolean schedule(SIPStackTimerTask task, long delay) {
		if(threadPoolExecutor.isShutdown()) {
			throw new IllegalStateException("The SIP Stack Timer has been stopped, no new tasks can be scheduled !");
		}
		ScheduledFuture<?> future = threadPoolExecutor.schedule(new ScheduledSipTimerTask(task), delay, TimeUnit.MILLISECONDS);
		task.setSipTimerTask(future);
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#scheduleWithFixedDelay(gov.nist.javax.sip.stack.SIPStackTimerTask, long, long)
	 */
	@Override
	public boolean scheduleWithFixedDelay(SIPStackTimerTask task, long delay,
			long period) {
		if(threadPoolExecutor.isShutdown()) {
			throw new IllegalStateException("The SIP Stack Timer has been stopped, no new tasks can be scheduled !");
		}
		ScheduledFuture<?> future = threadPoolExecutor.scheduleWithFixedDelay(new ScheduledSipTimerTask(task), delay, period, TimeUnit.MILLISECONDS);
		task.setSipTimerTask(future);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#start(gov.nist.javax.sip.SipStackImpl)
	 */
	public void start(SipStackImpl sipStack) {
		sipStackImpl= sipStack;		
		threadPoolExecutor.prestartAllCoreThreads();
		schedulePurgeTaskIfNeeded();
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			logger.logInfo("the sip stack timer " + this.getClass().getName() + " has been started");
		}
	}
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#cancel(gov.nist.javax.sip.stack.SIPStackTimerTask)
	 */
	@Override
	public boolean cancel(SIPStackTimerTask task) {
		boolean cancelled = false;
		ScheduledFuture<?> sipTimerTask = (ScheduledFuture<?>) ((SIPStackTimerTask)task).getSipTimerTask();
		if(sipTimerTask != null) {
			task.cleanUpBeforeCancel();			
			task.setSipTimerTask(null);
			threadPoolExecutor.remove((Runnable)sipTimerTask);
			cancelled = sipTimerTask.cancel(false);
		} 
		return cancelled;
	}

	private class ScheduledSipTimerTask implements Runnable {
		private SIPTimerTask task;

		public ScheduledSipTimerTask(SIPTimerTask task) {
			this.task= task;			
		}
		
		public void run() {
			 try {
				 // task can be null if it has been cancelled
				 if(task != null) {
					 Thread.currentThread().setName(task.getClass().getName());
					 task.runTask();
				 }
	        } catch (Throwable e) {
	            System.out.println("SIP stack timer task failed due to exception:");
	            e.printStackTrace();
	        }
		}				
	}

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#isStarted()
	 */
	public boolean isStarted() {
		return !threadPoolExecutor.isShutdown();
	}
	
}
