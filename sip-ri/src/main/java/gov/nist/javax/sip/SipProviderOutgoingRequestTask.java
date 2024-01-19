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
package gov.nist.javax.sip;

import java.io.IOException;
import java.text.ParseException;

import javax.sip.address.Hop;

import gov.nist.core.CommonLogger;
import gov.nist.core.InternalErrorHandler;
import gov.nist.core.LogLevels;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;

public class SipProviderOutgoingRequestTask implements SIPTask {
        private StackLogger logger = CommonLogger.getLogger(SipProviderOutgoingRequestTask.class);
        private SipProviderImpl sipProvider;
        private String id;
        private long startTime;
        private SIPRequest sipRequest;
        private Hop hop;

        public SipProviderOutgoingRequestTask(SipProviderImpl sipProviderImpl, SIPRequest sipRequest, Hop hop) {
            this.sipProvider = sipProviderImpl;
            startTime = System.currentTimeMillis();
            if(sipRequest.isNullRequest()) {
                this.id = hop.toString();
            } else {
                this.id = sipRequest.getCallId().getCallId();
            }
            this.sipRequest = sipRequest;
            this.hop = hop;
        }

        @Override
        public void execute() {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("Executing task with id: " + id);
            }
            SipStackImpl sipStack = (SipStackImpl) sipProvider.getSipStack();
            try {
                /*
                 * JvB: Via branch should already be OK, dont touch it here? Some
                 * apps forward statelessly, and then it's not set. So set only when
                 * not set already, dont overwrite CANCEL branch here..
                 */
                if (!sipRequest.isNullRequest()) {
                    Via via = sipRequest.getTopmostVia();
                    String branch = via.getBranch();
                    if (branch == null || branch.length() == 0) {
                        via.setBranch(sipRequest.getTransactionId());
                    }
                }
                MessageChannel messageChannel = null;
                if (sipProvider.listeningPoints.containsKey(hop.getTransport()
                        .toUpperCase())) {
                    messageChannel = sipStack.createRawMessageChannel(
                            sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(),
                            sipProvider.getListeningPoint(hop.getTransport()).getPort(), hop);
                }

                if (messageChannel != null) {
                    messageChannel.sendMessage((SIPMessage) sipRequest, hop);
                    /**
                     * Notifying the application layer of the message sent out in the same thread
                     */
                    if(sipProvider.getSipListener() instanceof SipListenerExt) {
                        ((SipListenerExt) sipProvider.getSipListener()).processMessageSent(
                            sipRequest, null);
                    }
                } else {
                    if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG)) {
                        logger.logDebug("Could not create a message channel for " + hop.toString()
                                + " listeningPoints = " + sipProvider.listeningPoints);
                    }
                    IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(
                        this, 
                        gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionFailure, 
                        sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(), 
                        sipProvider.getListeningPoint(hop.getTransport()).getPort(), 
                        hop.getHost(), 
                        hop.getPort(), 
                        hop.getTransport());

                    sipProvider.handleEvent(exceptionEvent, null);
                }
            } catch (IOException ex) {
                // not needed as we throw the exception below and it pollutes the logs
                // if (logger.isLoggingEnabled()) {
                // logger.logException(ex);
                // }

                if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG)) {
                    logger.logDebug("Could not create a message channel for " + hop.toString() + " listeningPoints = "
                            + sipProvider.listeningPoints + " because of an IO issue " + ex.getMessage());
                }

                IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(
                    this, 
                    gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionFailure, 
                    sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(), 
                    sipProvider.getListeningPoint(hop.getTransport()).getPort(), 
                    hop.getHost(), 
                    hop.getPort(), 
                    hop.getTransport());

                sipProvider.handleEvent(exceptionEvent, null);
            } catch (ParseException ex1) {
                InternalErrorHandler.handleException(ex1);
            } finally {
                if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG))
                    logger.logDebug(
                            "done sending " + sipRequest.getMethod() + " to hop "
                                    + hop);
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