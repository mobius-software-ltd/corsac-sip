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
package gov.nist.javax.sip.stack;

import gov.nist.core.executor.Task;

/**
 * Thread that triggers the connection failure event on the message channel 
 * 
 * @author Jean Deruelle
 */
public class NettyConnectionFailureThread implements Task {
	NettyStreamMessageChannel messageChannel;
	long startTime;

	public NettyConnectionFailureThread(NettyStreamMessageChannel messageChannel) {
		this.messageChannel = messageChannel;
		startTime = System.currentTimeMillis();
	}

	@Override
	public void execute() {
		messageChannel.triggerConnectFailure();
	}

	@Override
	public long getStartTime() {
		return startTime;
	}

	@Override
	public String getId() {
		return "" + startTime;
	}

	@Override
	public String getTaskName() {
		return NettyConnectionFailureThread.class.getName().concat("-").concat(getId());
	}
}