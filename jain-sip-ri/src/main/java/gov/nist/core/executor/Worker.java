package gov.nist.core.executor;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;

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
public class Worker  implements Runnable
{
	private static StackLogger logger = CommonLogger.getLogger(Worker.class);

	private static final long TASK_POLL_INTERVAL = 25L;
	
	private CountableQueue<gov.nist.core.executor.Task> queue;
	private boolean isRunning;
	
	public Worker(CountableQueue<Task> queue, boolean isRunning)
	{
		this.queue = queue;
		this.isRunning = isRunning;
	}

	@Override
	public void run()
	{
		try
		{
			while (isRunning)
			{
				Task task = this.queue.take();
				if (task != null)
				{
                    if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
                        logger.logDebug("Executing task: " + task.getTaskName() + " with unique id: " + task.getId() + " from queue: " + queue);
                    }
					task.execute();
				}
                Thread.sleep(TASK_POLL_INTERVAL);
			}
		}
		catch (InterruptedException e)
		{			
		}
		catch (Exception e)
		{
			logger.logError("WORKER THREAD CAUGHT UNEXPECTED EXCEPTION!!! " + e.getClass().getSimpleName() + "," + e.getMessage(), e);			
		}
	}

	public void stop()
	{
		this.isRunning = false;
	}
}
