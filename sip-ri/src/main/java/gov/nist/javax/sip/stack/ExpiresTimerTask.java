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

import javax.sip.Timeout;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionState;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import gov.nist.javax.sip.stack.timers.SipTimerTaskData;

class ExpiresTimerTask extends SIPStackTimerTask {
    private static StackLogger logger = CommonLogger.getLogger(ExpiresTimerTask.class);
    private SIPClientTransactionImpl clientTransaction;
    private ExpiresTimerTaskData data;

    public ExpiresTimerTask(SIPClientTransactionImpl clientTransaction) {
        super(ExpiresTimerTask.class.getSimpleName());        
        this.clientTransaction = clientTransaction;
        this.data = new ExpiresTimerTaskData(clientTransaction.getBranch());
    }

    @Override
    public void runTask() {
        SipProviderImpl provider = clientTransaction.getSipProvider();

        if (clientTransaction.getState() != TransactionState.TERMINATED) {
            TimeoutEvent tte = new TimeoutEvent(provider, clientTransaction, Timeout.TRANSACTION);
            provider.handleEvent(tte, clientTransaction);
        } else {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("state = " + clientTransaction.getState());
            }
        }
    }

    @Override
    public String getId() {
        Request request = clientTransaction.getRequest();
        if (request != null && request instanceof SIPRequest) {
            return ((SIPRequest) request).getCallIdHeader().getCallId();
        } else {
            return clientTransaction.originalRequestCallId;
        }
    }

    

    @Override
    public SipTimerTaskData getData() {                
        return data;
    }

    class ExpiresTimerTaskData extends SipTimerTaskData {
        private String clientTransactionId;        

        public ExpiresTimerTaskData(String clientTransactionId) {
            this.clientTransactionId = clientTransactionId;
        }

        public String getClientTransactionId() {
            return clientTransactionId;
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            clientTransactionId = in.readUTF();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(clientTransactionId);
        }
    }

}
