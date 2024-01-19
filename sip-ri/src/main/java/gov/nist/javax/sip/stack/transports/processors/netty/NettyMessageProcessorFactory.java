/*
 * Mobius Software LTD
 * Copyright 2023, Mobius Software LTD and individual contributors
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
package gov.nist.javax.sip.stack.transports.processors.netty;

import java.io.IOException;
import java.net.InetAddress;

import javax.sip.ListeningPoint;

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessor;
import gov.nist.javax.sip.stack.transports.processors.MessageProcessorFactory;

/**
 * Netty Based Message Processor Factory extending the default
 * MessageProcessorFactory
 * 
 * @author Jean Deruelle
 */
public class NettyMessageProcessorFactory implements MessageProcessorFactory {

        private static final String USE_TLS_GATEWAY_OPTION = "gov.nist.javax.sip.USE_TLS_GATEWAY";

        @Override
        public MessageProcessor createMessageProcessor(
                        SIPTransactionStack sipStack, InetAddress ipAddress, int port,
                        String transport) throws IOException {
                // For Netty, we don't allow the user to specify an infinite # of threads
                int threadPoolSize = sipStack.getThreadPoolSize();
                if (threadPoolSize <= 0) {
                        sipStack.setThreadPoolSize(1);
                }
                if (transport.equalsIgnoreCase(ListeningPoint.UDP)) {
                        NettyDatagramMessageProcessor udpMessageProcessor = new NettyDatagramMessageProcessor(
                                        ipAddress, sipStack, port);
                        // sipStack.udpFlag = true;
                        return udpMessageProcessor;
                } else if (transport.equalsIgnoreCase(ListeningPoint.TCP)) {
                        NettyStreamMessageProcessor nettyTcpMessageProcessor = new NettyStreamMessageProcessor(
                                        ipAddress, sipStack, port, ListeningPoint.TCP);
                        // this.tcpFlag = true;
                        return nettyTcpMessageProcessor;
                } else if (transport.equalsIgnoreCase(ListeningPoint.TLS)) {
                        NettyStreamMessageProcessor tlsMessageProcessor = new NettyStreamMessageProcessor(
                                        ipAddress, sipStack, port, ListeningPoint.TLS);
                        // this.tlsFlag = true;
                        return tlsMessageProcessor;
                } else if (transport.equalsIgnoreCase(ListeningPoint.SCTP)) {
                        NettyStreamMessageProcessor sctpMessageProcessor = new NettyStreamMessageProcessor(
                                        ipAddress, sipStack, port, ListeningPoint.SCTP);
                        // this.tlsFlag = true;
                        return sctpMessageProcessor;
                } else if (transport.equalsIgnoreCase(ListeningPointExt.WS)) {
                        if ("true".equals(((SipStackImpl) sipStack).getConfigurationProperties()
                                        .getProperty(USE_TLS_GATEWAY_OPTION))) {
                                MessageProcessor mp = new NettyStreamMessageProcessor(
                                                ipAddress, sipStack, port, ListeningPointExt.WSS);
                                // mp.setTransport = "WS";
                                return mp;
                        } else {
                                MessageProcessor mp = new NettyStreamMessageProcessor(
                                                ipAddress, sipStack, port, ListeningPointExt.WS);
                                // mp.transport = "WS";
                                return mp;
                        }

                } else if (transport.equalsIgnoreCase(ListeningPointExt.WSS)) {

                        if ("true".equals(((SipStackImpl) sipStack).getConfigurationProperties()
                                        .getProperty(USE_TLS_GATEWAY_OPTION))) {
                                MessageProcessor mp = new NettyStreamMessageProcessor(
                                                ipAddress, sipStack, port, ListeningPointExt.WSS);
                                // mp.transport = "WSS";
                                return mp;
                        } else {
                                MessageProcessor mp = new NettyStreamMessageProcessor(
                                                ipAddress, sipStack, port, ListeningPointExt.WSS);
                                // mp.transport = "WSS";
                                return mp;
                        }
                } else {
                        throw new IllegalArgumentException("bad transport");
                }
        }

}
