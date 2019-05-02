/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 Untied States Code Section 105, works of NIST
* employees are not subject to copyright protection in the United States
* and are considered to be in the public domain.  As a result, a formal
* license is not needed to use the software.
*
* This software is provided by NIST as a service and is expressly
* provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
* Permission to use this software is contingent upon your acceptance
* of the terms of this agreement
*
* .
*
 */
package gov.nist.javax.sip.stack;

import gov.nist.core.Clock;
import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.core.SystemClock;
import gov.nist.javax.sip.stack.timers.SipTimer;
import java.lang.management.ManagementFactory;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SocketTimeoutAuditor extends SIPStackTimerTask implements SocketAuditorMBean {

    public static final String MBEAN_NAME = "gov.nist.javax.sip.stack:type=%sSocketAuditor";

    private static StackLogger logger = CommonLogger.getLogger(SocketTimeoutAuditor.class);
    private long nioSocketMaxIdleTime;
    private ConcurrentHashMap<SocketChannel, NioTcpMessageChannel> channelMap;
    private SipTimer timer;
    private long auditFrequency;
    private int maxIterations = 100;
    private int removedSockets = 0;
    private Clock clock;

    public SocketTimeoutAuditor(String transport, long nioSocketMaxIdleTime, ConcurrentHashMap<SocketChannel, NioTcpMessageChannel> channelMap, SipTimer timer) {
        this.nioSocketMaxIdleTime = nioSocketMaxIdleTime;
        this.channelMap = channelMap;
        this.timer = timer;
        timer.schedule(this, nioSocketMaxIdleTime);
        this.auditFrequency = nioSocketMaxIdleTime;
        final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        this.clock = new SystemClock();
        try {
            final StandardMBean mbean = new StandardMBean(this, SocketAuditorMBean.class);
            String name = String.format(MBEAN_NAME, transport);
            ObjectName mbeanName = new ObjectName(name);
            mbeanServer.registerMBean(mbean, mbeanName);
        } catch (Exception e) {
            logger.logError("Could not register MBean " + MBEAN_NAME, e);
        }
    }

    @Override
    public Object getThreadHash() {
        return null;
    }

    public void runTask() {
        long auditStartTS = clock.millis();
        removedSockets = 0;
        logger.logInfo("Start Task time : " + auditStartTS);
        try {
            // Reworked the method for https://java.net/jira/browse/JSIP-471
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("keys to check for inactivity removal " + channelMap.keySet());
            }
            Enumeration<NioTcpMessageChannel> entriesIterator = channelMap.elements();
            int iterations = 0;
            while (entriesIterator.hasMoreElements() && iterations < maxIterations) {
                NioTcpMessageChannel messageChannel = entriesIterator.nextElement();
                SocketChannel socketChannel = messageChannel.getSocketChannel();
                long elapsedSinceLastAct = auditStartTS - messageChannel.getLastActivityTimestamp();
                if (elapsedSinceLastAct > nioSocketMaxIdleTime) {
                    logger.logInfo("Remove socket : " + messageChannel.key);
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("Will remove socket " + messageChannel.key + " lastActivity="
                                + messageChannel.getLastActivityTimestamp() + " current= "
                                + auditStartTS + " socketChannel = "
                                + socketChannel);
                    }
                    messageChannel.close();
                    removedSockets = removedSockets + 1;
                    Thread.sleep(50);
                } else {
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug("don't remove socket " + messageChannel.key + " as lastActivity="
                                + messageChannel.getLastActivityTimestamp() + " and current= "
                                + auditStartTS + " socketChannel = "
                                + socketChannel);
                    }
                }
                iterations = iterations + 1;
            }
        } catch (Exception anything) {
            logger.logError("Exception in SocketTimeoutAuditor : ", anything);
        }
        logger.logInfo("End Task time : " + removedSockets);
        //schedule next audit
        timer.schedule(this, auditFrequency);
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public Integer getRemovedSockets() {
        return removedSockets;
    }

    public void setRemovedSockets(int removedSockets) {
        this.removedSockets = removedSockets;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Integer getChannelSize() {
        return channelMap.size();
    }

}
