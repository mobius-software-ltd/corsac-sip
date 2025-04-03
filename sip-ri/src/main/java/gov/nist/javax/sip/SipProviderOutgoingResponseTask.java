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

import javax.sip.address.Hop;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogLevels;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.executor.SIPTask;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.MessageTooLongException;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;

public class SipProviderOutgoingResponseTask implements SIPTask {
	private StackLogger logger = CommonLogger.getLogger(SipProviderOutgoingResponseTask.class);
	private SipProviderImpl sipProvider;
	private String id;
	private long startTime;
	private SIPResponse sipResponse;
	private Hop hop;

	public SipProviderOutgoingResponseTask(SipProviderImpl sipProviderImpl, SIPResponse sipResponse, Hop hop) {
		this.sipProvider = sipProviderImpl;
		startTime = System.currentTimeMillis();
		this.id = sipResponse.getCallId().getCallId();
		this.sipResponse = sipResponse;
		this.hop = hop;
	}

	@Override
	public void execute() {
		if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
			logger.logDebug("Executing task with id: " + id);
		}

		SipStackImpl sipStack = (SipStackImpl) sipProvider.getSipStack();
		try {
			ListeningPointImpl listeningPoint = (ListeningPointImpl) sipProvider.getListeningPoint(hop.getTransport());
			if (listeningPoint == null) {
				IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(sipResponse, this,
						gov.nist.javax.sip.IOExceptionEventExt.Reason.NoListeninPointForTransport, null, -1,
						hop.getHost(), hop.getPort(), hop.getTransport());

				sipProvider.handleEvent(exceptionEvent, null);
				return;
				// throw new SipException(
				// "whoopsa daisy! no listening point found for transport "
				// + hop.getTransport());
			}

			MessageChannel messageChannel = sipStack.createRawMessageChannel(
					sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(), listeningPoint.port, hop);
			// Fix for https://github.com/RestComm/jain-sip/issues/133
			if (messageChannel != null) {
				messageChannel.sendMessage(sipResponse);
				/**
				 * Notifying the application layer of the message sent out in the same thread
				 */
				if (sipProvider.getSipListener() instanceof SipListenerExt) {
					((SipListenerExt) sipProvider.getSipListener()).processMessageSent(sipResponse, null);
				}
			} else {
				if (logger.isLoggingEnabled(LogLevels.TRACE_DEBUG)) {
					logger.logDebug("Could not create a message channel for " + hop.toString() + " listeningPoints = "
							+ sipProvider.listeningPoints);
				}
				IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(sipResponse, this,
						gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionFailure,
						sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(),
						sipProvider.getListeningPoint(hop.getTransport()).getPort(), hop.getHost(), hop.getPort(),
						hop.getTransport());

				sipProvider.handleEvent(exceptionEvent, null);
				// throw new SipException(
				// "Could not create a message channel for "
				// + hop.toString());
			}
		} catch (IOException ex) {
			IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(sipResponse, this,
					gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionFailure,
					sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(),
					sipProvider.getListeningPoint(hop.getTransport()).getPort(), hop.getHost(), hop.getPort(),
					hop.getTransport());

			sipProvider.handleEvent(exceptionEvent, null);
			// throw new SipException(ex.getMessage());
		} catch (MessageTooLongException ex) {
			IOExceptionEventExt exceptionEvent = new IOExceptionEventExt(sipResponse, this,
					gov.nist.javax.sip.IOExceptionEventExt.Reason.MessageToLong,
					sipProvider.getListeningPoint(hop.getTransport()).getIPAddress(),
					sipProvider.getListeningPoint(hop.getTransport()).getPort(), hop.getHost(), hop.getPort(),
					hop.getTransport());

			sipProvider.handleEvent(exceptionEvent, null);
			// throw new SipException(ex.getMessage());
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