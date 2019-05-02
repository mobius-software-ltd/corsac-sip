/*
 */
package gov.nist.javax.sip.stack;

import gov.nist.core.Clock;
import gov.nist.javax.sip.stack.timers.SipTimer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

public class SocketTimeoutAuditorTest {

    public SocketTimeoutAuditorTest() {
    }

    /**
     * Check if auditor is able to finish when traffic is adding more sockets
     * concurrently
     *
     * @throws InterruptedException
     */
    @Test
    public void testConcurrentCloseIdle() throws InterruptedException {
        long maxIdleTime = 100;
        Integer maxIterations = 100;
        final ConcurrentHashMap<SocketChannel, NioTcpMessageChannel> channelMap = new ConcurrentHashMap<SocketChannel, NioTcpMessageChannel>();
        SipTimer timer = Mockito.mock(SipTimer.class);
        Clock clock = Mockito.mock(Clock.class);
        when(clock.millis()).thenReturn(10000L);
        SocketTimeoutAuditor auditor = new SocketTimeoutAuditor("tcp", maxIdleTime, channelMap, timer);
        auditor.setClock(clock);
        auditor.setMaxIterations(maxIterations);
        ScheduledExecutorService newFixedThreadPool = Executors.newScheduledThreadPool(20);
        newFixedThreadPool.scheduleAtFixedRate(new SocketCreator(channelMap), 0, 5, TimeUnit.MILLISECONDS);
        //allow creators to accumulate some sockets
        Thread.sleep(100);
        //ensure there are at least maxIterations sockets
        for (int i = 0; i < maxIterations; i++) {
            new SocketCreator(channelMap).run();
        }
        //run auditor
        auditor.runTask();
        //ensure auditor removed maxIterations sockets
        assertEquals(maxIterations, auditor.getRemovedSockets());
        //ensure creators added more than removed sockets
        assertTrue(auditor.getChannelSize() > 0);
    }

    /**
     * Adds a socket which will always meet the idle timeout condition
     */
    class SocketCreator implements Callable<Integer>, Runnable {

        private ConcurrentHashMap<SocketChannel, NioTcpMessageChannel> channelMap;

        public SocketCreator(ConcurrentHashMap<SocketChannel, NioTcpMessageChannel> channelMap) {
            this.channelMap = channelMap;
        }

        @Override
        public Integer call() {
            SocketChannel channel = Mockito.mock(SocketChannel.class);

            NioTcpMessageChannel tcpChannel = Mockito.mock(NioTcpMessageChannel.class);
            //return 0 to ensure is idle
            when(tcpChannel.getLastActivityTimestamp()).thenReturn(0L);
            when(tcpChannel.getSocketChannel()).thenReturn(channel);

            channelMap.put(channel, tcpChannel);
            return 0;
        }

        @Override
        public void run() {
            this.call();
        }

    }

}
