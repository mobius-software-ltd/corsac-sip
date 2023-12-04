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

import com.mobius.software.common.dal.timers.PeriodicQueuedTasks;
import com.mobius.software.common.dal.timers.Timer;

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
	private static StackLogger logger = CommonLogger.getLogger(MobiusSipTimer.class);	

	protected SipStackImpl sipStackImpl;
	private PeriodicQueuedTasks<Timer> periodicQueue;	
	private AtomicBoolean started = new AtomicBoolean(false);
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#stop()
	 */
	public void stop() {	
		started.set(false);	
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			logger.logInfo("the Mobius sip stack timer " + this.getClass().getName() + " has been stopped");
		}
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long)
	 */
	public boolean schedule(SIPStackTimerTask task, long delay) {
		MobiusSipTimerTask timerTask = new MobiusSipTimerTask(this, task, delay);
		task.setSipTimerTask(timerTask);
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Scheduling timer  " + task + " with delay " + delay);
		}
		periodicQueue.store(timerTask.getRealTimestamp(),timerTask); 		
		
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#scheduleWithFixedDelay(gov.nist.javax.sip.stack.SIPStackTimerTask, long, long)
	 */
	public boolean scheduleWithFixedDelay(SIPStackTimerTask task, long delay,
			long period) {
		MobiusSipTimerTask timerTask = new MobiusSipTimerTask(this, task, delay, period);
		task.setSipTimerTask(timerTask);
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Scheduling timer  " + task + " with delay " + delay + " and period " + period);
		}
		periodicQueue.store(timerTask.getRealTimestamp(),timerTask); 		

		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#start(gov.nist.javax.sip.SipStackImpl)
	 */
	public void start(SipStackImpl sipStack) {
		sipStackImpl= sipStack;		
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
		if((MobiusSipTimerTask)task.getSipTimerTask() != null) {	
			((MobiusSipTimerTask)task.getSipTimerTask()).stop();		
			return true;
		}
		return false;
	}

	public PeriodicQueuedTasks<Timer> getPeriodicQueue() {
		return periodicQueue;
	}

	public void setPeriodicQueue(PeriodicQueuedTasks<Timer> periodicQueue) {
		this.periodicQueue = periodicQueue;
	}	

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#isStarted()
	 */
	public boolean isStarted() {
		return started.get();
	}
	
}
