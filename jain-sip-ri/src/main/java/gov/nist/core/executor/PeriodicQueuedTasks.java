package gov.nist.core.executor;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class PeriodicQueuedTasks<T extends SIPTimer>
{
	private ConcurrentHashMap<Long, ConcurrentLinkedQueue<T>> queues = new ConcurrentHashMap<Long, ConcurrentLinkedQueue<T>>();
	private MessageProcessorExecutorBak messageProcessorExecutor;
	private ConcurrentLinkedQueue<T> passAwayQueue = new ConcurrentLinkedQueue<T>();

	private long period;
	private AtomicLong previousRun = new AtomicLong(0);

	public PeriodicQueuedTasks(long period, MessageProcessorExecutorBak messageProcessorExecutor)
	{
		this.messageProcessorExecutor = messageProcessorExecutor;
		this.period = period;
	}

	public long getPeriod()
	{
		return period;
	}

	public long getPreviousRun()
	{
		return previousRun.get();
	}

	public void store(long timestamp, T task)
	{
		Long periodTime = timestamp - timestamp % period;
		ConcurrentLinkedQueue<T> queue;
		if (previousRun.get() >= periodTime)
			passAwayQueue.offer(task);
		else
		{
			queue = queues.get(periodTime);
			if (queue == null)
			{
				queue = new ConcurrentLinkedQueue<T>();
				ConcurrentLinkedQueue<T> oldQueue = queues.putIfAbsent(
						periodTime, queue);
				if (oldQueue != null)
					queue = oldQueue;
			}

			if (previousRun.get() >= periodTime)
			{
				passAwayQueue.offer(task);
				queues.remove(periodTime);
			} else
				queue.offer(task);
		}
	}

	public void executePreviousPool(long timestamp)
	{
		Long originalTime = (timestamp - timestamp % period - period);
		Long periodTime = originalTime;

		ConcurrentLinkedQueue<T> queue = null;
		T current;

		do
		{
			if (!previousRun.compareAndSet(0, periodTime))
				periodTime = previousRun.addAndGet(period);

			queue = queues.remove(periodTime);
			if (queue != null)
			{
				while ((current = queue.poll()) != null)
				{
					if (current.getRealTimestamp() < (periodTime + period))
						messageProcessorExecutor.addTaskFirst(current);
				}
			}
		} while (periodTime.longValue() < originalTime.longValue());

		while ((current = passAwayQueue.poll()) != null)
		{
			if (current.getRealTimestamp() < (periodTime + period))
				messageProcessorExecutor.addTaskFirst(current);
		}
	}

	public ConcurrentHashMap<Long, ConcurrentLinkedQueue<T>> getQueues()
	{
		return queues;
	}
}