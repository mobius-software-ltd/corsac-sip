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

import javax.sip.header.ExpiresHeader;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.header.Expires;
import gov.nist.javax.sip.message.SIPRequest;

public class ClientTransactionOutgoingMessageTask implements SIPTask {
    private StackLogger logger = CommonLogger.getLogger(ClientTransactionOutgoingMessageTask.class);
    private SIPClientTransactionImpl clientTransaction;
    private String id;
    private long startTime;
    private SIPRequest sipRequest;

    public ClientTransactionOutgoingMessageTask(SIPClientTransactionImpl clientTransaction, SIPRequest sipRequest) {
        this.clientTransaction = clientTransaction;
        startTime = System.currentTimeMillis();
        this.id = sipRequest.getCallId().getCallId();
        this.sipRequest = sipRequest;
    }

    @Override
    public void execute() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("Executing task with id: " + id);
        }

        // if (isInviteTransaction()) {
        // SIPDialog dialog = getDefaultDialog();

        // if (dialog != null && dialog.isBackToBackUserAgent()) {
        // // Block sending re-INVITE till we see the ACK.
        // if (!dialog.takeAckSem()) {
        // // throw new SipException("Failed to take ACK semaphore");
        // }

        // }
        // }
        // Only map this after the fist request is sent out.
        clientTransaction.isMapped = true;
        // Time extracted from the Expires header.
        int expiresTime = -1;

        if (sipRequest.getHeader(ExpiresHeader.NAME) != null) {
            Expires expires = (Expires) sipRequest.getHeader(ExpiresHeader.NAME);
            expiresTime = expires.getExpires();
        }
        // This is a User Agent. The user has specified an Expires time. Start a timer
        // which will check if the tx is terminated by that time.
        if (clientTransaction.getDefaultDialog() != null && clientTransaction.isInviteTransaction() && expiresTime != -1
                && clientTransaction.expiresTimerTask == null) {
            clientTransaction.expiresTimerTask = new ExpiresTimerTask(clientTransaction);
            // josemrecio - https://java.net/jira/browse/JSIP-467
            clientTransaction.sipStack.getTimer().schedule(clientTransaction.expiresTimerTask, Long.valueOf(expiresTime) * 1000L);

        }
        try {
            clientTransaction.sendMessage(sipRequest);
            /**
             * Notifying the application layer of the message sent out in the same thread
             */
            if(clientTransaction.getSipProvider().getSipListener() instanceof SipListenerExt) {
                ((SipListenerExt) clientTransaction.getSipProvider().getSipListener()).processMessageSent(
                    sipRequest, clientTransaction);
            }
        } catch (IOException ex) {
            // setState(TransactionState._TERMINATED);
            // if (expiresTimerTask != null) {
            // sipStack.getTimer().cancel(expiresTimerTask);
            // }
            // throw new SipException(ex.getMessage() == null ? "IO Error sending request" :
            // ex.getMessage(),
            // ex);
            clientTransaction.raiseIOExceptionEvent(gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionError);

        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }
}
