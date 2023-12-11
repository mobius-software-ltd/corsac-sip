/*
 * Mobius Software LTD
 * Copyright 2019 - 2023, Mobius Software LTD and individual contributors
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
package gov.nist.core.executor;

import com.mobius.software.common.dal.timers.CountableQueue;
import com.mobius.software.common.dal.timers.PeriodicQueuedTasks;
import com.mobius.software.common.dal.timers.Task;
import com.mobius.software.common.dal.timers.Timer;
import com.mobius.software.common.dal.timers.WorkerPool;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;


public class MessageProcessorExecutor {
	private static StackLogger logger = CommonLogger.getLogger(MessageProcessorExecutor.class);
	private WorkerPool workerPool;
	private int workersNumber;

	public void start(int workersNumber, long taskInterval) {
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Starting MessageProcessorExecutor with workersNumber: " + workersNumber + " and taskInterval: " + taskInterval);
		}
		this.workersNumber = workersNumber;	
		workerPool = new WorkerPool(taskInterval, Boolean.FALSE);
		workerPool.start(workersNumber);
	}

	public void stop() {
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Stopping MessageProcessorExecutor");
		}
		workerPool.stop();
		workerPool = null;
	}

	public void addTaskFirst(SIPTask task) {
		CountableQueue<Task> queue = getQueue(task.getId());
		if (queue != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("Adding Task First : "  + task.getTaskName() + " " + task.getId() + ", Queue Size: "  + queue.size());
			}
			queue.offerFirst(task);
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {				
				logger.logDebug("Queue Size: "  + queue.size());
			}
		}
	}

	public void addTaskLast(SIPTask task) {
		CountableQueue<Task> queue = getQueue(task.getId());
		if (queue != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("Adding Task Last : "  + task.getTaskName() + " " + task.getId() + ", Queue Size: "  + queue.size());
			}
			queue.offerLast(task);
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {				
				logger.logDebug("Queue Size: "  + queue.size());
			}
		}
	}

	private CountableQueue<Task> getQueue(String id) {
		int index = findQueueIndex(id);
		// if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
		// 	logger.logDebug("Queue index: " + index + " for id: " + id);
		// }
		return workerPool.getLocalQueue(index);		
	}

	public int findQueueIndex(String id) {
		return Math.abs(id.hashCode()) % workersNumber;
	}

	public PeriodicQueuedTasks<Timer> getPeriodicQueue() {
		return workerPool.getPeriodicQueue();
	}	
}