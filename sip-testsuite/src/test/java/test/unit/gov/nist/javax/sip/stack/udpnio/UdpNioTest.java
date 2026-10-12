package test.unit.gov.nist.javax.sip.stack.udpnio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Properties;

import org.junit.After;
import org.junit.Test;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import io.netty.channel.epoll.Epoll;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * Channels that fail to bind are no longer kept, NIO as a fallback should work now
 */
public class UdpNioTest {

    private static final StackLogger logger = CommonLogger.getLogger(UdpNioTest.class);

    private static final int TIMEOUT = 10000;
    private static final int THREAD_POOL_SIZE = 64;

    private Shootist shootist;
    private Shootme shootme;

    @After
    public void tearDown() throws Exception {
        if (shootist != null) {
            shootist.stop();
        }
        if (shootme != null) {
            shootme.stop();
        }
    }

    /** Both stacks on NIO with 64 threads; INVITE, 180, 200, ACK, BYE, 200 must all arrive. */
    @Test(timeout = 60000)
    public void testCallOverNioUdp() throws Exception {
        start(nioUdp(), nioUdp());
        assertCall();
    }

    /** NIO with 64 threads: only the first channel binds the port, only that one is kept. */
    @Test(timeout = 60000)
    public void testNioKeepsOnlyBoundChannel() throws Exception {
        shootme = new Shootme(nextPort(), nioUdp());
        assertEquals(1, shootme.getNioBoundChannels());
    }

    /** Only the UAS on NIO with 64 threads, the UAC on one channel: the UAS responses must arrive. */
    @Test(timeout = 60000)
    public void testNioUasResponses() throws Exception {
        start(defaultNettyUdp(1), nioUdp());
        assertCall();
    }

    /** Same call on native epoll, where all 64 channels bind; fails when epoll is not available. */
    @Test(timeout = 60000)
    public void testCallOverEpollUdp() throws Exception {
        if (!Epoll.isAvailable()) {
            logger.logError("Can't run this test without epoll", Epoll.unavailabilityCause());
            fail("Can't run this test without epoll");
        }
        start(defaultNettyUdp(THREAD_POOL_SIZE), defaultNettyUdp(THREAD_POOL_SIZE));
        assertCall();
    }

    private void start(Properties shootistProperties, Properties shootmeProperties) {
        int shootistPort = nextPort();
        int shootmePort = nextPort();
        shootme = new Shootme(shootmePort, shootmeProperties);
        shootist = new Shootist(shootistPort, shootmePort, shootistProperties);
    }

    private static Properties nioUdp() {
        return stackProperties(NioUdpMessageProcessorFactory.class.getName(), THREAD_POOL_SIZE);
    }

    private static Properties defaultNettyUdp(int threadPoolSize) {
        return stackProperties(NettyMessageProcessorFactory.class.getName(), threadPoolSize);
    }

    private static Properties stackProperties(String messageProcessorFactory, int threadPoolSize) {
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", String.valueOf(threadPoolSize));
        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", messageProcessorFactory);
        return properties;
    }

    /** a port that is a multiple of the pool size hit the one bound NIO channel and hid the bug */
    private static int nextPort() {
        int port;
        do {
            port = NetworkPortAssigner.retrieveNextPort();
        } while (port % THREAD_POOL_SIZE == 0);
        return port;
    }

    private void assertCall() throws Exception {
        shootist.sendInvite();
        assertTrue("INVITE must reach the UAS", AssertUntil.assertUntil(shootme.getInviteAssertion(), TIMEOUT));
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("ACK must reach the UAS", AssertUntil.assertUntil(shootme.getAckAssertion(), TIMEOUT));
        shootist.sendBye();
        assertTrue("BYE must be answered 200", AssertUntil.assertUntil(shootist.getByeOkAssertion(), TIMEOUT));
        assertFalse("unexpected transaction timeout on the UAC", shootist.isTimeoutSeen());
    }
}
