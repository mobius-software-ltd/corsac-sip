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

import com.mobius.software.common.dal.timers.Timer;

import gov.nist.javax.sip.stack.SIPStackTimerTask;

public class MobiusSipTimerTask implements Timer {
    private MobiusSipTimer timer;
    private SIPStackTimerTask task;
    private long startTime;
    private AtomicLong timestamp;
    private AtomicLong period;

    public MobiusSipTimerTask(MobiusSipTimer timer, SIPStackTimerTask task, long timeout) {
        this.timer = timer;
        this.task = task;
        this.startTime = System.currentTimeMillis();
        this.timestamp = new AtomicLong(System.currentTimeMillis() + timeout);
        this.period = new AtomicLong(-1);
    }

    public MobiusSipTimerTask(MobiusSipTimer timer, SIPStackTimerTask task, long timeout, long period) {
        this(timer, task, timeout);
        this.period = new AtomicLong(period);
    }

    @Override
    public void execute() {
        if (timestamp.get() < Long.MAX_VALUE) {
            try {
                // task can be null if it has been cancelled
                if (task != null) {
                    Thread.currentThread().setName(task.getTaskName());
                    task.runTask();
                }
            } catch (Throwable e) {
                System.out.println("SIP stack timer task failed due to exception:");
                e.printStackTrace();
            }
            if (period.get() > 0) {
                timestamp.set(System.currentTimeMillis() + period.get());
                timer.getPeriodicQueue().store(timestamp.get(), this);
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
        timestamp.set(Long.MAX_VALUE);
    }
}
