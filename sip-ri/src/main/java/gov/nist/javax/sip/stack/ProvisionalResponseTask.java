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

import javax.sip.TransactionState;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import gov.nist.javax.sip.stack.timers.SipTimerTaskData;

class ProvisionalResponseTask extends SIPStackTimerTask {
    private StackLogger logger = CommonLogger.getLogger(ProvisionalResponseTask.class);
    ProvisionalResponseTaskData data;

    SIPServerTransactionImpl serverTransaction;
    SIPDialog sipDialog;

    public ProvisionalResponseTask(SIPDialog sipDialog, SIPServerTransactionImpl serverTransaction) {
        super(ProvisionalResponseTask.class.getSimpleName());
        this.data = new ProvisionalResponseTaskData(serverTransaction.getBranch(), sipDialog.getDialogId());
        this.serverTransaction = serverTransaction;
        this.sipDialog = sipDialog;
        data.ticks = SIPTransactionImpl.T1;
        data.ticksLeft = data.ticks;
    }

    public void runTask() {        
        SIPTransactionStack sipStack = serverTransaction.sipStack;
        /*
         * The reliable provisional response is passed to the transaction layer
         * periodically
         * with an interval that starts at T1 seconds and doubles for each
         * retransmission (T1
         * is defined in Section 17 of RFC 3261). Once passed to the server transaction,
         * it is
         * added to an internal list of unacknowledged reliable provisional responses.
         * The
         * transaction layer will forward each retransmission passed from the UAS core.
         *
         * This differs from retransmissions of 2xx responses, whose intervals cap at T2
         * seconds. This is because retransmissions of ACK are triggered on receipt of a
         * 2xx,
         * but retransmissions of PRACK take place independently of reception of 1xx.
         */
        // If the transaction has terminated,
        if (serverTransaction.isTerminated()) {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("canceling ProvisionalResponseTask as tx is terminated");
            }
            sipStack.getTimer().cancel(this);

        } else {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("ProvisionalResponseTask ticksLeft " + data.ticksLeft);
            }
            data.ticksLeft--;
            if (data.ticksLeft == -1) {
                try {
                    serverTransaction.resendLastResponseAsBytes(
                        sipDialog.pendingReliableResponseAsBytes);
                } catch (IOException e) {
                    if (logger.isLoggingEnabled())
                        logger.logException(e);
                    // serverTransaction.setState(TransactionState._TERMINATED);
                    serverTransaction.raiseErrorEvent(SIPTransactionErrorEvent.TRANSPORT_ERROR);
                }
                
                data.ticksLeft = 2 * data.ticks;
                data.ticks = data.ticksLeft;
                // timer H MUST be set to fire in 64*T1 seconds for all transports. Timer H
                // determines when the server
                // transaction abandons retransmitting the response
                if (data.ticksLeft >= SIPTransactionImpl.TIMER_H) {
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("canceling ProvisionalResponseTask");
                    }
                    sipStack.getTimer().cancel(this);
                    // If a reliable provisional response is retransmitted for 64*T1 seconds
                    // without reception of a corresponding PRACK, the UAS SHOULD reject the
                    // original request with a 5xx response. This should be done at app level
                    serverTransaction.setState(TransactionState._TERMINATED);
                    serverTransaction.fireTimeoutTimer();
                }
            }

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
    public SipTimerTaskData getData() {
        return data;
    }

    class ProvisionalResponseTaskData extends SipTimerTaskData {
        private String serverTransactionId;        
        private String dialogId;  
        int ticks;
        int ticksLeft;      

        public ProvisionalResponseTaskData(String serverTransactionId, String dialogId) {
            this.serverTransactionId = serverTransactionId;
            this.dialogId = dialogId;  
        }

        public String getServerTransactionId() {
            return serverTransactionId;
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            serverTransactionId = in.readUTF();
            dialogId = in.readUTF();
            ticks = in.readInt();
            ticksLeft = in.readInt();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeUTF(serverTransactionId);
            out.writeUTF(dialogId);
            out.writeInt(ticks);
            out.writeInt(ticksLeft);
        }
    }
}
