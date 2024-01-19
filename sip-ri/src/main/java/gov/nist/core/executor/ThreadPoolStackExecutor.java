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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.mobius.software.common.dal.timers.Task;

import gov.nist.core.CommonLogger;
import gov.nist.core.NamingThreadFactory;
import gov.nist.core.StackLogger;


public class ThreadPoolStackExecutor implements StackExecutor {
	private static StackLogger logger = CommonLogger.getLogger(ThreadPoolStackExecutor.class);	
	private int workersNumber;
	private CopyOnWriteArrayList<LinkedBlockingQueue<Task>> queues;
	private CopyOnWriteArrayList<ScheduledThreadPoolExecutor> threadPoolExecutors;

	public void start(int workersNumber, long taskInterval) {
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Starting ThreadPoolStackExecutor with workersNumber: " + workersNumber + " and taskInterval: " + taskInterval);
		}
		this.workersNumber = workersNumber;	
		threadPoolExecutors = new CopyOnWriteArrayList<>();
		queues = new CopyOnWriteArrayList<>();
		for(int i = 0; i < workersNumber; i++) {
			LinkedBlockingQueue<Task> queue = new LinkedBlockingQueue<>();
			queues.add(queue);
			ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor(1, new NamingThreadFactory("thread_pool_stack_executor_" + i));
			threadPoolExecutors.add(threadPoolExecutor);
			threadPoolExecutor.scheduleWithFixedDelay(new Dispatch(queue), taskInterval, taskInterval, TimeUnit.MILLISECONDS);
		}
	}

	public void stop() {
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Stopping ThreadPoolStackExecutor");
		}
		for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : threadPoolExecutors) {
			scheduledThreadPoolExecutor.shutdown();	
		}		
		threadPoolExecutors.clear();;
		queues.clear();
		queues = null;
	}

	public void addTaskFirst(SIPTask task) {
		LinkedBlockingQueue<Task> queue = getQueue(task.getId());
		if (queue != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("Adding Task First : "  + task + " " + task.getId() + ", Queue Size: "  + queue.size());
			}
			queue.add(task);
			
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {				
				logger.logDebug("Queue Size: "  + queue.size());
			}
		}
	}

	public void addTaskLast(SIPTask task) {
		LinkedBlockingQueue<Task> queue = getQueue(task.getId());
		if (queue != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("Adding Task Last : "  + task + " " + task.getId() + ", Queue Size: "  + queue.size());
			}
			queue.offer(task);

			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {				
				logger.logDebug("Queue Size: "  + queue.size());
			}
		}
	}

	private LinkedBlockingQueue<Task> getQueue(String id) {
		int index = findQueueIndex(id);
		// if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
		// 	logger.logDebug("Queue index: " + index + " for id: " + id);
		// }
		return queues.get(index);		
	}

	public int findQueueIndex(String id) {
		return Math.abs(id.hashCode()) % workersNumber;
	}	

	public class Dispatch implements Runnable {    	
    	LinkedBlockingQueue<Task> queue;
    	
		public Dispatch(LinkedBlockingQueue<Task> queue) {
    		this.queue = queue;
    	}

        @Override
        public void run() {   
			Task task = queue.poll();
			while (task != null) {				
				task.execute();
				task = queue.poll();
			}            			                    
        }	       
    };
}