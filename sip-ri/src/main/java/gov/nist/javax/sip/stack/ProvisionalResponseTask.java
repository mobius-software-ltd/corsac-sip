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

import javax.sip.TransactionState;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

class ProvisionalResponseTask extends SIPStackTimerTask {
    private StackLogger logger = CommonLogger.getLogger(ProvisionalResponseTask.class);
    
    SIPServerTransactionImpl serverTransaction;
    SIPDialog sipDialog;
    int ticks;
    int ticksLeft;     

    public ProvisionalResponseTask(SIPDialog sipDialog, SIPServerTransactionImpl serverTransaction) {
        super(ProvisionalResponseTask.class.getSimpleName());
        this.serverTransaction = serverTransaction;
        this.sipDialog = sipDialog;
        ticks = SIPTransactionImpl.T1;
        ticksLeft = ticks;
    }

    public void runTask() {                
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
            sipDialog.stopReliableResponseTimer();

        } else {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("ProvisionalResponseTask ticksLeft " + ticksLeft);
            }
            ticksLeft--;
            if (ticksLeft == -1) {
                try {
                    serverTransaction.resendLastResponseAsBytes(
                        sipDialog.pendingReliableResponseAsBytes);
                } catch (IOException e) {
                    if (logger.isLoggingEnabled())
                        logger.logException(e);
                    // serverTransaction.setState(TransactionState._TERMINATED);
                    serverTransaction.raiseErrorEvent(SIPTransactionErrorEvent.TRANSPORT_ERROR);
                }
                
                ticksLeft = 2 * ticks;
                ticks = ticksLeft;
                // timer H MUST be set to fire in 64*T1 seconds for all transports. Timer H
                // determines when the server
                // transaction abandons retransmitting the response
                if (ticksLeft >= SIPTransactionImpl.TIMER_H) {
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("canceling ProvisionalResponseTask");
                    }
                    sipDialog.stopReliableResponseTimer();
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
}
