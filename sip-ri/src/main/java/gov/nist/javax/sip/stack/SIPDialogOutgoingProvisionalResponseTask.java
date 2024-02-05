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

import javax.sip.header.RSeqHeader;

import gov.nist.core.CommonLogger;
import gov.nist.core.InternalErrorHandler;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.header.RSeq;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;

public class SIPDialogOutgoingProvisionalResponseTask implements SIPTask {
    private StackLogger logger =
        CommonLogger.getLogger(SIPDialogOutgoingProvisionalResponseTask.class);

    SIPServerTransactionImpl serverTransaction;
    SIPDialog sipDialog;
    private String id;
    private long startTime;

    SIPResponse relResponse;

    public SIPDialogOutgoingProvisionalResponseTask(SIPDialog sipDialog, SIPServerTransactionImpl serverTransaction,
            SIPResponse sipResponse) {
        this.serverTransaction = serverTransaction;
        this.sipDialog = sipDialog;
        startTime = System.currentTimeMillis();
        this.id = sipResponse.getCallId().getCallId();
        this.relResponse = sipResponse;
    }

    @Override
    public void execute() {
        try {
            /*
             * In addition, it MUST contain a Require header field containing the option tag
             * 100rel,
             * and MUST include an RSeq header field.
             */
            RSeq rseq = (RSeq) relResponse.getHeader(RSeqHeader.NAME);
            if (relResponse.getHeader(RSeqHeader.NAME) == null) {
                rseq = new RSeq();
                relResponse.setHeader(rseq);
            }

            if (sipDialog.rseqNumber < 0) {
                sipDialog.rseqNumber = (int) (Math.random() * 1000);
            }
            sipDialog.rseqNumber++;
            rseq.setSeqNumber(sipDialog.rseqNumber);
            sipDialog.pendingReliableRSeqNumber = rseq.getSeqNumber();
            // start the timer task which will retransmit the reliable response
            // until the PRACK is received. Cannot send a second provisional.
            sipDialog.setLastResponse(serverTransaction, relResponse);

            SIPResponse reliableResponse = (SIPResponse) relResponse;
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug("Storing reliableResponse " + reliableResponse + " for Dialog " + this + " with dialog Id " + sipDialog.dialogId + " in STX " + serverTransaction);

            sipDialog.pendingReliableResponseAsBytes = reliableResponse.encodeAsBytes(serverTransaction.getTransport());
            sipDialog.pendingReliableResponseMethod = reliableResponse.getCSeq().getMethod();
            sipDialog.pendingReliableCSeqNumber = reliableResponse.getCSeq().getSeqNumber();
            
            // if (this.getDialog() != null && interlockProvisionalResponses) {
            // boolean acquired = this.provisionalResponseSem.tryAcquire(1,
            // TimeUnit.SECONDS);
            // if (!acquired) {
            // throw new SipException("Unacknowledged reliable response");
            // }
            // }
            // moved the task scheduling before the sending of the message to overcome
            // Issue 265 : https://jain-sip.dev.java.net/issues/show_bug.cgi?id=265
            sipDialog.provisionalResponseTask = new ProvisionalResponseTask(sipDialog, serverTransaction);
            serverTransaction.sipStack.getTimer().scheduleWithFixedDelay(sipDialog.provisionalResponseTask, 0,
                    SIPTransactionStack.BASE_TIMER_INTERVAL);
            // provisionalResponseTask.runTask();
            serverTransaction.sendMessage((SIPMessage) relResponse);
            /**
             * Notifying the application layer of the message sent out in the same thread
             */
            if (serverTransaction.getSipProvider().getSipListener() instanceof SipListenerExt) {
                ((SipListenerExt) serverTransaction.getSipProvider().getSipListener()).processMessageSent(
                        relResponse, serverTransaction);
            }
        } catch (Exception ex) {
            InternalErrorHandler.handleException(ex);
            serverTransaction.raiseErrorEvent(SIPTransactionErrorEvent.TRANSPORT_ERROR);
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