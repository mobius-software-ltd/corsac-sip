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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.sip.message.Request;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import gov.nist.javax.sip.stack.timers.SipTimerTaskData;

/**
 * This timer task is used for alerting the application to send retransmission
 * alerts.
 */
class RetransmissionAlertTimerTask extends SIPStackTimerTask {
    private RetransmissionAlertTimerTaskData data;
    SIPServerTransactionImpl serverTransaction;

    public RetransmissionAlertTimerTask(SIPServerTransactionImpl serverTransaction, String dialogId) {
        super(RetransmissionAlertTimerTask.class.getSimpleName());
        this.data = new RetransmissionAlertTimerTaskData(serverTransaction.getBranch(), dialogId);
        this.serverTransaction = serverTransaction;
        data.ticks = SIPTransactionImpl.T1;
        data.ticksLeft = data.ticks;
        // Fix from http://java.net/jira/browse/JSIP-443
        // by mitchell.c.ackerman
    }

    public void runTask() {        
        data.ticksLeft--;
        if (data.ticksLeft == -1) {
            serverTransaction.fireRetransmissionTimer();
            data.ticksLeft = 2 * data.ticks;
        }

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

    @Override
    public RetransmissionAlertTimerTaskData getData() {
        return data;
    }

    class RetransmissionAlertTimerTaskData extends SipTimerTaskData {
        String serverTransactionId;        
        String dialogId;  
        int ticks;
        int ticksLeft;      

        public RetransmissionAlertTimerTaskData(String serverTransactionId, String dialogId) {
            this.serverTransactionId = serverTransactionId;
            this.dialogId = dialogId;  
        }

        public String getServerTransactionId() {
            return serverTransactionId;
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            serverTransactionId = in.readUTF();
            dialogId = in.readUTF();
            ticks = in.readInt();
            ticksLeft = in.readInt();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(serverTransactionId);
            out.writeUTF(dialogId);
            out.writeInt(ticks);
            out.writeInt(ticksLeft);
        }

        public String getDialogId() {
            return dialogId;
        }
    }

}
