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
package gov.nist.javax.sip.stack.timers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.mobius.software.common.dal.timers.PeriodicQueuedTasks;
import com.mobius.software.common.dal.timers.Timer;
import com.mobius.software.common.dal.timers.WorkerPool;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.SIPStackTimerTask;

/**
 * Implementation of the SIP Timer based on Mobius Software LTD Timers Library
 * 
 * @author Jean Deruelle
 *
 */
public class MobiusSipTimer implements SipTimer {
	private static StackLogger logger = CommonLogger.getLogger(ScheduledExecutorSipTimer.class);
	private static final Integer MAX_WORKERS = 4;

	protected SipStackImpl sipStackImpl;
	
	private WorkerPool workerPool = null; 
	private AtomicBoolean started = new AtomicBoolean(false);
	private PeriodicQueuedTasks<Timer> periodicQueue;
	
    
	public MobiusSipTimer() {
		
	}
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#stop()
	 */
	public void stop() {		
		if(isStarted()) {
			workerPool.stop();	
			started.set(false);
			if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
				logger.logInfo("the Mobius sip stack timer " + this.getClass().getName() + " has been stopped");
			}
		}			
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long)
	 */
	public boolean schedule(SIPStackTimerTask task, long delay) {
		MobiusSipTimerTask timerTask = new MobiusSipTimerTask(task, delay);
		task.setSipTimerTask(timerTask);
		periodicQueue.store(timerTask.getRealTimestamp(),timerTask); 		
		
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#scheduleWithFixedDelay(gov.nist.javax.sip.stack.SIPStackTimerTask, long, long)
	 */
	public boolean scheduleWithFixedDelay(SIPStackTimerTask task, long delay,
			long period) {
		MobiusSipTimerTask timerTask = new MobiusSipTimerTask(task, delay, period);
		task.setSipTimerTask(timerTask);
		periodicQueue.store(timerTask.getRealTimestamp(),timerTask); 		

		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#start(gov.nist.javax.sip.SipStackImpl)
	 */
	public void start(SipStackImpl sipStack) {
		sipStackImpl= sipStack;
		workerPool = new WorkerPool();
		if(sipStack.getThreadPoolSize() <= 0) {
			workerPool.start(MAX_WORKERS);
		} else {
			workerPool.start(sipStack.getThreadPoolSize());
		}
		periodicQueue = workerPool.getPeriodicQueue();
		started.set(true);
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			logger.logInfo("the sip stack timer " + this.getClass().getName() + " has been started");
		}
	}
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#cancel(gov.nist.javax.sip.stack.SIPStackTimerTask)
	 */
	public boolean cancel(SIPStackTimerTask task) {		
		((MobiusSipTimerTask)task.getSipTimerTask()).stop();

		return true;
	}

	private class MobiusSipTimerTask implements Timer {
		private SIPStackTimerTask task;
		private long startTime;
		private AtomicLong timestamp;
		private AtomicLong period;

		public MobiusSipTimerTask(SIPStackTimerTask task, long timeout) {
			this.task = task;	
			this.startTime=System.currentTimeMillis();
			this.timestamp = new AtomicLong(System.currentTimeMillis() + timeout);		
			this.period = new AtomicLong(-1);
		}

		public MobiusSipTimerTask(SIPStackTimerTask task, long timeout, long period) {
			this.task = task;	
			this.startTime=System.currentTimeMillis();
			this.timestamp = new AtomicLong(System.currentTimeMillis() + timeout);		
			this.period = new AtomicLong(period);
		}
		
		@Override
		public void execute() {
			if(timestamp.get()<Long.MAX_VALUE) {
				try {
					// task can be null if it has been cancelled
					if(task != null) {
						Thread.currentThread().setName(task.getTaskName());
						task.runTask();
					}
				} catch (Throwable e) {
					System.out.println("SIP stack timer task failed due to exception:");
					e.printStackTrace();
				}
				if(period.get() > 0) {
					timestamp.set(System.currentTimeMillis() + period.get());
					periodicQueue.store(timestamp.get(),this); 
				}
			}
		}

		@Override
		public long getStartTime() 
		{
			return startTime;
		}

		@Override
		public Long getRealTimestamp() 
		{
			return timestamp.get();
		}

		@Override
		public void stop() 
		{
			timestamp.set(Long.MAX_VALUE);
		}			
	}

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#isStarted()
	 */
	public boolean isStarted() {
		return started.get();
	}
	
}
