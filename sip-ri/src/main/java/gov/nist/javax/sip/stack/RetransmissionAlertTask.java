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
package gov.nist.javax.sip.stack;

import javax.sip.message.Request;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

/**
 * This timer task is used for alerting the application to send retransmission
 * alerts.
 */
class RetransmissionAlertTimerTask extends SIPStackTimerTask {
    String serverTransactionId;        
    String dialogId;  
    int ticks;
    SIPServerTransactionImpl serverTransaction;

    public RetransmissionAlertTimerTask(SIPServerTransactionImpl serverTransaction, String dialogId) {
        super(RetransmissionAlertTimerTask.class.getSimpleName());
        // this.data = new RetransmissionAlertTimerTaskData(serverTransaction.getBranch(), dialogId);
        this.dialogId = dialogId;  
        this.serverTransaction = serverTransaction;
        this.ticks = SIPTransactionImpl.T1;
        // Fix from http://java.net/jira/browse/JSIP-443
        // by mitchell.c.ackerman
    }

    public void runTask() {        
    	serverTransaction.fireRetransmissionTimer();
        this.ticks = 2 * this.ticks;
        serverTransaction.sipStack.getTimer().schedule(this, this.ticks * SIPTransactionStack.BASE_TIMER_INTERVAL);
    }

    @Override
    public String getId() {
        Request request = serverTransaction.getRequest();
        if (request != null && request instanceof SIPRequest) {
            return ((SIPRequest) request).getCallIdHeader().getCallId();
        } else {
            return serverTransaction.originalRequestCallId;
        }
    }
}
