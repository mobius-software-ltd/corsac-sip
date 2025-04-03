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
import java.text.ParseException;

import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.address.Hop;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.InternalErrorHandler;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;

public class DialogOutgoingMessageProcessingTask implements SIPTask {
    private StackLogger logger = CommonLogger.getLogger(DialogOutgoingMessageProcessingTask.class);
    private String id;
    private long startTime;
    private SIPRequest dialogRequest;
    private SIPDialog sipDialog;
    private SIPClientTransaction clientTransaction;

    public DialogOutgoingMessageProcessingTask(
            SIPDialog sipDialog,
            SIPClientTransaction clientTransaction,
            SIPRequest sipRequest) {
        startTime = System.currentTimeMillis();
        this.id = sipRequest.getCallId().getCallId();
        this.dialogRequest = sipRequest;
        this.sipDialog = sipDialog;
        this.clientTransaction = clientTransaction;
    }

    @Override
    public void execute() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("Executing task with id: " + id);
        }
        SipProviderImpl sipProvider = sipDialog.getSipProvider();
        SipStackImpl sipStack = (SipStackImpl) sipDialog.getSipProvider().getSipStack();

        try {
            // Set the dialog back pointer.
            clientTransaction.setDialog(sipDialog, sipDialog.getDialogId());

            sipDialog.addTransaction(clientTransaction);
            // Enable the retransmission filter for the transaction

            clientTransaction.setTransactionMapped(true);

            From from = (From) dialogRequest.getFrom();
            To to = (To) dialogRequest.getTo();

            // Caller already did the tag assignment -- check to see if the
            // tag assignment is OK.
            if (sipDialog.getLocalTag() != null && from.getTag() != null
                    && !from.getTag().equals(sipDialog.getLocalTag()))
                throw new SipException("From tag mismatch expecting  "
                        + sipDialog.getLocalTag());

            if (sipDialog.getRemoteTag() != null && to.getTag() != null
                    && !to.getTag().equals(sipDialog.getRemoteTag())) {
                if (logger.isLoggingEnabled())
                    logger.logWarning(
                            "SIPDialog::sendRequest:To header tag mismatch expecting "
                                    + sipDialog.getRemoteTag());
            }
            /*
             * The application is sending a NOTIFY before sending the response of
             * the dialog.
             */
            if (sipDialog.getLocalTag() == null
                    && dialogRequest.getMethod().equals(Request.NOTIFY)) {
                if (!sipDialog.getMethod().equals(Request.SUBSCRIBE))
                    throw new SipException(
                            "Trying to send NOTIFY without SUBSCRIBE Dialog!");
                sipDialog.setLocalTag(from.getTag());

            }

            try {
                if (sipDialog.getLocalTag() != null)
                    from.setTag(sipDialog.getLocalTag());
                if (sipDialog.getRemoteTag() != null)
                    to.setTag(sipDialog.getRemoteTag());

            } catch (ParseException ex) {
                InternalErrorHandler.handleException(ex);
            }

            Hop hop = ((SIPClientTransaction) clientTransaction).getNextHop();
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug(
                        "SIPDialog::sendRequest:Using hop = " + hop.getHost() + " : " + hop.getPort());
            }
            
            try {
                // FIX ME: firstTransactionPort can be different with the updated sip-provider
                // after failover.
                // Using directly the port from new updated Sip-provider.
                MessageChannel messageChannel = sipStack.createRawMessageChannel(
                        sipProvider.getListeningPoint(hop.getTransport())
                                .getIPAddress(),
                        sipProvider.getListeningPoint(hop.getTransport()).getPort(), hop);

                MessageChannel oldChannel = ((SIPClientTransaction) clientTransaction)
                        .getMessageChannel();

                // Remove this from the connection cache if it is in the
                // connection
                // cache and is not yet active.
                oldChannel.uncache();

                // Not configured to cache client connections.
                if (!sipStack.cacheClientConnections) {
                    oldChannel.decreaseUseCount();
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                        logger.logDebug(
                                "SIPDialog::sendRequest:oldChannel: useCount " + oldChannel.getUseCount());

                }

                if (messageChannel == null) {
                    /*
                     * At this point the procedures of 8.1.2 and 12.2.1.1 of RFC3261
                     * have been tried but the resulting next hop cannot be resolved
                     * (recall that the exception thrown is caught and ignored in
                     * SIPStack.createMessageChannel() so we end up here with a null
                     * messageChannel instead of the exception handler below). All
                     * else failing, try the outbound proxy in accordance with
                     * 8.1.2, in particular: This ensures that outbound proxies that
                     * do not add Record-Route header field values will drop out of
                     * the path of subsequent requests. It allows endpoints that
                     * cannot resolve the first Route URI to delegate that task to
                     * an outbound proxy.
                     * 
                     * if one considers the 'first Route URI' of a request
                     * constructed according to 12.2.1.1 to be the request URI when
                     * the route set is empty.
                     */
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                        logger.logDebug(
                                "Null message channel using outbound proxy !");
                    Hop outboundProxy = sipStack.getRouter(dialogRequest)
                            .getOutboundProxy();
                    if (outboundProxy == null)
                        throw new SipException("No route found! hop=" + hop);
                    messageChannel = sipStack.createRawMessageChannel(
                            sipProvider.getListeningPoint(
                                    outboundProxy.getTransport())
                                    .getIPAddress(),
                            sipDialog.firstTransactionPort, outboundProxy);
                    if (messageChannel != null)
                        ((SIPClientTransaction) clientTransaction)
                                .setEncapsulatedChannel(messageChannel);
                } else {
                    ((SIPClientTransaction) clientTransaction)
                            .setEncapsulatedChannel(messageChannel);

                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug(
                                "SIPDialog::sendRequest:using message channel " + messageChannel);

                    }

                }

                if (messageChannel != null)
                    messageChannel.increaseUseCount();

                // See if we need to release the previously mapped channel.
                if ((!sipStack.cacheClientConnections) && oldChannel != null
                        && oldChannel.getUseCount() <= 0)
                    oldChannel.close();
            } catch (Exception ex) {
                if (logger.isLoggingEnabled())
                    logger.logException(ex);
                throw new SipException("Could not create message channel", ex);
            }

            try {
                // Increment before setting!!
                long cseqNumber = dialogRequest.getCSeq() == null ? sipDialog.getLocalSeqNumber()
                        : dialogRequest.getCSeq().getSeqNumber();
                if (cseqNumber > sipDialog.getLocalSeqNumber()) {
                    sipDialog.setLocalSequenceNumber(cseqNumber);
                } else {
                    sipDialog.setLocalSequenceNumber(sipDialog.getLocalSeqNumber() + 1);
                }
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug(
                            "SIPDialog::sendRequest:setting Seq Number to " + sipDialog.getLocalSeqNumber());

                }
                dialogRequest.getCSeq().setSeqNumber(sipDialog.getLocalSeqNumber());
            } catch (InvalidArgumentException ex) {
                logger.logFatalError(ex.getMessage());
            }

            ((SIPClientTransaction) clientTransaction)
                    .sendMessage(dialogRequest);
            /*
             * Note that if the BYE is rejected then the Dialog should bo back
             * to the ESTABLISHED state so we only set state after successful
             * send.
             */
            if (dialogRequest.getMethod().equals(Request.BYE)) {
                sipDialog.byeSent = true;
                /*
                 * Dialog goes into TERMINATED state as soon as BYE is sent.
                 * ISSUE 182.
                 */
                if (sipDialog.isTerminatedOnBye()) {
                    sipDialog.setState(DialogState._TERMINATED);
                }
            }

            /**
             * Notifying the application layer of the message sent out in the same thread
             */
            if(sipProvider.getSipListener() instanceof SipListenerExt) {
                ((SipListenerExt) sipProvider.getSipListener()).processMessageSent(dialogRequest, clientTransaction);
            }

        } catch (SipException e) {
            sipDialog.raiseIOException(gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionError, clientTransaction.getHost(),
                    clientTransaction.getPort(),
                    sipProvider.getListeningPoint(
                            clientTransaction.getTransport()).getIPAddress(),
                    sipDialog.firstTransactionPort,
                    clientTransaction.getTransport());
        } catch (MessageTooLongException e) {
            sipDialog.raiseIOException(gov.nist.javax.sip.IOExceptionEventExt.Reason.MessageToLong, clientTransaction.getHost(),
                    clientTransaction.getPort(),
                    sipProvider.getListeningPoint(
                            clientTransaction.getTransport()).getIPAddress(),
                    sipDialog.firstTransactionPort,
                    clientTransaction.getTransport());
        } catch (IOException e) {
            sipDialog.raiseIOException(gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionError, clientTransaction.getHost(),
                    clientTransaction.getPort(),
                    sipProvider.getListeningPoint(
                            clientTransaction.getTransport()).getIPAddress(),
                    sipDialog.firstTransactionPort,
                    clientTransaction.getTransport());
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