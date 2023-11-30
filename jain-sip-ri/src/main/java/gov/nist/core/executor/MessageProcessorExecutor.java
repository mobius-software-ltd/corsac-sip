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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class MessageProcessorExecutor {
	// private static StackLogger logger = CommonLogger.getLogger(MessageProcessorExecutor.class);
	private static final long PERIOD = 25L;
	private CopyOnWriteArrayList<CountableQueue<Task>> callIdQueuesList;
	private PeriodicQueuedTasks<Timer> periodicQueue;
	
	private ScheduledExecutorService timersExecutor;
	private ExecutorService workersExecutors;

	public MessageProcessorExecutor() {
		callIdQueuesList = new CopyOnWriteArrayList<CountableQueue<Task>>();
		periodicQueue=new PeriodicQueuedTasks<Timer>(PERIOD, this);		
	}

	public void start(int workersNumber) {
		timersExecutor = Executors.newScheduledThreadPool(1);
		timersExecutor.scheduleAtFixedRate(new TimersRunner(periodicQueue), 0, PERIOD, TimeUnit.MILLISECONDS);
		
		workersExecutors = Executors.newFixedThreadPool(workersNumber);

		List<Worker> workers = new ArrayList<Worker>();
		for (int i = 0; i < workersNumber; i++) {
			CountableQueue<Task> queue = new CountableQueue<Task>();
			callIdQueuesList.add(queue);
			workers.add(new Worker(queue, true));
			workersExecutors.execute(workers.get(i));
		}
	}

	public void stop() {
		workersExecutors.shutdown();
		workersExecutors = null;
	}

	public void addTaskFirst(Task task) {
		CountableQueue<Task> queue = getQueue(task.getId());
		if (queue != null) {
			queue.offerFirst(task);
		}
	}

	public void addTaskLast(Task task) {
		CountableQueue<Task> queue = getQueue(task.getId());
		if (queue != null) {
			queue.offerLast(task);
		}
	}

	private CountableQueue<Task> getQueue(String id) {
		int index = findQueueIndex(id);
		// if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
		// 	logger.logDebug("Queue index: " + index + " for id: " + id);
		// }
		return callIdQueuesList.get(index);		
	}

	public int findQueueIndex(String id) {
		return Math.abs(id.hashCode()) % callIdQueuesList.size();
	}

	public PeriodicQueuedTasks<Timer> getPeriodicQueue() {
		return periodicQueue;
	}	
}