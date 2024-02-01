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

import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.SipException;
import javax.sip.TransactionState;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.Utils;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

public class ServerTransactionOutgoingMessageTask implements SIPTask {
    private StackLogger logger = CommonLogger.getLogger(ServerTransactionOutgoingMessageTask.class);
    private SIPServerTransactionImpl serverTransaction;
    private String id;
    private long startTime;
    private SIPResponse sipResponse;
    private SIPDialog sipDialog;

    public ServerTransactionOutgoingMessageTask(SIPServerTransactionImpl serverTransaction, SIPResponse sipResponse,
            SIPDialog sipDialog) {
        this.serverTransaction = serverTransaction;
        startTime = System.currentTimeMillis();
        this.id = sipResponse.getCallId().getCallId();
        this.sipResponse = sipResponse;
        this.sipDialog = sipDialog;
    }

    @Override
    public void execute() {
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("Executing task with id: " + id + " on Dialog " + sipDialog);
        }
        final int statusCode = sipResponse.getStatusCode();
        final String responseMethod = sipResponse.getCSeq().getMethod();        
        try {
            
            // Sending the final response cancels the
            // pending response task.
            if (sipDialog != null && sipDialog.pendingReliableResponseAsBytes != null && sipResponse.isFinalResponse()) {
                serverTransaction.sipStack.getTimer().cancel(sipDialog.provisionalResponseTask);
                sipDialog.provisionalResponseTask = null;
            }

            // Dialog checks. These make sure that the response
            // being sent makes sense.
            if (sipDialog != null) {
                if (statusCode / 100 == 2
                        && SIPTransactionStack.isDialogCreatingMethod(responseMethod)) {
                    if (sipDialog.getLocalTag() == null && sipResponse.getToTag() == null) {
                        // Trying to send final response and user forgot to set
                        // to
                        // tag on the response -- be nice and assign the tag for
                        // the user.
                        sipResponse.getTo().setTag(Utils.getInstance().generateTag());
                    } else if (sipDialog.getLocalTag() != null && sipResponse.getToTag() == null) {
                        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                            logger.logDebug("assigning toTag : serverTransaction = " + this + " dialog "
                                    + sipDialog + " tag = " + sipDialog.getLocalTag());
                        }
                        sipResponse.setToTag(serverTransaction.getDialog().getLocalTag());
                    } else if (sipDialog.getLocalTag() != null && sipResponse.getToTag() != null
                            && !sipDialog.getLocalTag().equals(sipResponse.getToTag())) {
                        throw new SipException("Tag mismatch dialogTag is "
                                + sipDialog.getLocalTag() + " responseTag is "
                                + sipResponse.getToTag() + " on response " + sipResponse 
                                + " dialog = " + sipDialog + " serverTx = " + serverTransaction);
                    }
                }

                if (!sipResponse.getCallId().getCallId().equals(sipDialog.getCallId().getCallId())) {
                    throw new SipException("Dialog mismatch!");
                }
            }

            // Backward compatibility slippery slope....
            // Only set the from tag in the response when the
            // incoming request has a from tag.
            String fromTag = serverTransaction.originalRequestFromTag;
            if (serverTransaction.getRequest() != null) {
                fromTag = ((SIPRequest) serverTransaction.getRequest()).getFromTag();
            }
            if (fromTag != null && sipResponse.getFromTag() != null
                    && !sipResponse.getFromTag().equals(fromTag)) {
                throw new SipException("From tag of request does not match response from tag");
            } else if (fromTag != null) {
                sipResponse.getFrom().setTag(fromTag);
            } else {
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                    logger.logDebug("WARNING -- Null From tag in request!!");
            }
            // See if the dialog needs to be inserted into the dialog table
            // or if the state of the dialog needs to be changed.
            if (sipDialog != null && statusCode != 100) {
                sipDialog.setResponseTags(sipResponse);
                DialogState oldState = sipDialog.getState();
                sipDialog.setLastResponse(serverTransaction, sipResponse);
                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                    logger.logDebug("dialog oldstate: " + oldState + ", current state: " + serverTransaction.getDialog().getState());
                if (oldState == null && sipDialog.getState() == DialogState.TERMINATED) {
                    DialogTerminatedEvent event = new DialogTerminatedEvent(sipDialog
                            .getSipProvider(), sipDialog);

                    // Provide notification to the listener that the dialog has
                    // ended.
                    sipDialog.getSipProvider().handleEvent(event, serverTransaction);

                }

            } else if (sipDialog == null && serverTransaction.isInviteTransaction()
                    && serverTransaction.retransmissionAlertEnabled
                    && serverTransaction.retransmissionAlertTimerTask == null
                    && sipResponse.getStatusCode() / 100 == 2) {
                String dialogId = sipResponse.getDialogId(true);

                serverTransaction.retransmissionAlertTimerTask = new RetransmissionAlertTimerTask(serverTransaction, dialogId);
                serverTransaction.sipStack.retransmissionAlertTransactions.put(dialogId, serverTransaction);
                serverTransaction.sipStack.getTimer().scheduleWithFixedDelay(serverTransaction.retransmissionAlertTimerTask, 0,
                        SIPTransactionStack.BASE_TIMER_INTERVAL);
                // retransmissionAlertTimerTask.runTask();
            }

            // Send message after possibly inserting the Dialog
            // into the dialog table to avoid a possible race condition.

            serverTransaction.sendMessage(sipResponse);

            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug(
                        "checking if we need to start retrans timer dialog " + sipDialog + " response " + sipResponse);
            if (sipDialog != null) {
                sipDialog.startRetransmitTimer(serverTransaction, sipResponse);
            }

            /**
             * Notifying the application layer of the message sent out in the same thread
             */
            if(serverTransaction.getSipProvider().getSipListener() instanceof SipListenerExt) {
                ((SipListenerExt) serverTransaction.getSipProvider().getSipListener()).processMessageSent(
                    sipResponse, serverTransaction);
            }

        } catch (IOException ex) {
            if (logger.isLoggingEnabled())
                logger.logException(ex);
            serverTransaction.setState(TransactionState._TERMINATED);
            serverTransaction.raiseErrorEvent(SIPTransactionErrorEvent.TRANSPORT_ERROR);
            // throw new SipException(ex.getMessage(), ex);
        } catch (java.text.ParseException ex) {
            if (logger.isLoggingEnabled())
                logger.logException(ex);
            serverTransaction.setState(TransactionState._TERMINATED);
            serverTransaction.raiseErrorEvent(SIPTransactionErrorEvent.TRANSPORT_ERROR);
            // throw new SipException(ex1.getMessage(), ex1);
        } catch (SipException ex) {
            if (logger.isLoggingEnabled())
                logger.logException(ex);
            // setState(TransactionState._TERMINATED);
            serverTransaction.raiseErrorEvent(SIPTransactionErrorEvent.TRANSPORT_ERROR);
            // throw new SipException(ex1.getMessage(), ex1);
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
