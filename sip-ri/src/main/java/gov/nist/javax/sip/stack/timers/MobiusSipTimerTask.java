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

import java.util.concurrent.atomic.AtomicLong;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.MessageProcessorExecutor;
import gov.nist.core.executor.SIPTimer;

public class MobiusSipTimerTask implements SIPTimer {
    private static StackLogger logger = CommonLogger.getLogger(MobiusSipTimerTask.class);
    private MobiusSipTimer timer;
    private SIPTimerTask task;
    private long startTime;
    private AtomicLong timestamp;
    private AtomicLong period;
    private String id;
    private MessageProcessorExecutor messageProcessorExecutor;
    private String taskName;

    public MobiusSipTimerTask(MobiusSipTimer timer, SIPTimerTask task, long timeout, String taskName) {
        this.timer = timer;
        this.task = task;
        this.startTime = System.currentTimeMillis();
        this.timestamp = new AtomicLong(System.currentTimeMillis() + timeout);
        this.period = new AtomicLong(-1);
        this.id = task.getId();
        this.messageProcessorExecutor = (MessageProcessorExecutor) timer.sipStackImpl.getMessageProcessorExecutor();
        this.taskName = taskName;
    }

    public MobiusSipTimerTask(MobiusSipTimer timer, SIPTimerTask task, long timeout, long period, String taskName) {
        this(timer, task, timeout, taskName);
        this.period = new AtomicLong(period);
    }

    @Override
    public void execute() {
		if (timestamp.get() < Long.MAX_VALUE) {
            try {
                // task can be null if it has been cancelled
                if (task != null) {
                    Thread.currentThread().setName(task.getClass().getName());
                    task.runTask();
                }
            } catch (Exception e) {
                logger.logError("SIP stack timer task failed due to exception:", e);
                // e.printStackTrace();
            }
            if (period.get() > 0) {
            	startTime = System.currentTimeMillis();
                timestamp.set(timestamp.get() + period.get());
                if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                    logger.logDebug("Scheduling periodic task " + task + " with id " + task.getId() + 
                        " with period " + period.get() + " next execution at " + timestamp.get());
                }
                timer.getPeriodicQueue().store(timestamp.get(), this);
            } else {
                timestamp.set(Long.MAX_VALUE);
            }
        }
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public Long getRealTimestamp() {
        return timestamp.get();
    }

    @Override
    public void stop() {
        // Making sure we stop both one shot and periodic timers
        timestamp.set(Long.MAX_VALUE);
        period.set(-1);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Integer getQueueIndex() {
        return messageProcessorExecutor.findQueueIndex(id);
    }
    
    @Override
	public String printTaskDetails() {
		return "Task name: " + taskName + ", id: " + id;
	}
}
