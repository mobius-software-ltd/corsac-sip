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
package gov.nist.javax.sip.stack.timers;

public interface StackTimer {
    /**
	 * Schedule a new SIPTimerTask after the specified delay
	 * @param task the task to schedule
	 * @param delay the delay in milliseconds to schedule the task
	 * @return true if the task was correctly scheduled, false otherwise
	 */
	boolean schedule(SIPTimerTask task, long delay);
	
	/**
	 * Schedule a new SIPTimerTask after the specified delay
	 * @param task the task to schedule
	 * @param delay the delay in milliseconds to schedule the task
	 * @param period the period to run the task after it has been first scheduled 
	 * @return true if the task was correctly scheduled, false otherwise
	 */
	boolean scheduleWithFixedDelay(SIPTimerTask task, long delay, long period);

    /**
	 * cancel a previously scheduled SIPTimerTask task
	 * @param task task to cancel
	 * @return true if the task was cancelled, false otherwise
	 */
	boolean cancel(SIPTimerTask task);
}
