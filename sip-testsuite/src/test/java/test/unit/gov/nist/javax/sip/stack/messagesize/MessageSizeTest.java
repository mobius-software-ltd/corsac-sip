package test.unit.gov.nist.javax.sip.stack.messagesize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * Netty receive buffer settings (NETTY_RCVBUF_ALLOCATOR, NETTY_RCVBUF_ALLOCATOR_SIZE) over UDP and TCP:
 * FIXED 4096, MESSAGE that fits and one byte over; ADAPTIVE, 9k MESSAGE right away, with and
 * without a starting size.
 */
public class MessageSizeTest {

    private static final int TIMEOUT = 5000;
    private static final int FIXED_SIZE = 4096;

    private static final String UDP = "udp";
    private static final String TCP = "tcp";

    private static final String ALLOCATOR = "gov.nist.javax.sip.NETTY_RCVBUF_ALLOCATOR";
    private static final String ALLOCATOR_SIZE = "gov.nist.javax.sip.NETTY_RCVBUF_ALLOCATOR_SIZE";

    private int shootistPort;
    private int shootmePort;

    private Shootist shootist;
    private Shootme shootme;

    @Before
    public void setUp() throws Exception {
        shootistPort = NetworkPortAssigner.retrieveNextPort();
        shootmePort = NetworkPortAssigner.retrieveNextPort();
    }

    @After
    public void tearDown() throws Exception {
        if (shootist != null) {
            shootist.stop();
        }
        if (shootme != null) {
            shootme.stop();
        }
    }

    /** UDP, 4096 bytes: fits the buffer, 200. */
    @Test(timeout = 30000)
    public void testUdpFixedReadsMessageThatFits() throws Exception {
        start(UDP, fixed());

        assertDelivered(FIXED_SIZE);
    }

    /** UDP, 4097 bytes: the kernel cuts the last byte, the stack drops the MESSAGE. */
    @Test(timeout = 30000)
    public void testUdpFixedDropsMessageBiggerThanBuffer() throws Exception {
        start(UDP, fixed());

        assertNotDelivered(FIXED_SIZE + 1);
    }

    /** TCP, 4096 bytes: one read, 200. */
    @Test(timeout = 30000)
    public void testTcpFixedReadsMessageThatFits() throws Exception {
        start(TCP, fixed());

        assertDelivered(FIXED_SIZE);
    }

    /** TCP, 4097 bytes: two reads put back together, 200. */
    @Test(timeout = 30000)
    public void testTcpFixedReadsMessageBiggerThanBuffer() throws Exception {
        start(TCP, fixed());

        assertDelivered(FIXED_SIZE + 1);
    }

    /** UDP, no size so it starts at 2048, 9k: first datagram cut and dropped, buffer grows, the retransmission gets the 200. */
    @Test(timeout = 30000)
    public void testUdpAdaptiveRecoversFromMessageBiggerThanFirstBuffer() throws Exception {
        start(UDP, adaptive());

        assertDelivered(9 * 1024);
    }

    /** UDP, starts at 16384, 9k sent once without retransmissions: fits the first buffer, 200. */
    @Test(timeout = 30000)
    public void testUdpAdaptiveStartsAtConfiguredSize() throws Exception {
        start(UDP, properties(ALLOCATOR, "ADAPTIVE", ALLOCATOR_SIZE, "16384"));
        shootist.sendOnce = true;

        assertDelivered(9 * 1024);
    }

    /** TCP, no size so it starts at 2048, 9k: read in parts, 200. */
    @Test(timeout = 30000)
    public void testTcpAdaptiveReadsMessageBiggerThanFirstBuffer() throws Exception {
        start(TCP, adaptive());

        assertDelivered(9 * 1024);
    }

    private static Properties fixed() {
        return properties(ALLOCATOR, "FIXED", ALLOCATOR_SIZE, Integer.toString(FIXED_SIZE));
    }

    private static Properties adaptive() {
        return properties(ALLOCATOR, "ADAPTIVE");
    }

    private void start(String transport, Properties shootmeProperties) {
        shootme = new Shootme(shootmePort, transport, shootmeProperties);
        shootist = new Shootist(shootistPort, shootmePort, transport);
    }

    private void assertDelivered(int size) throws Exception {
        send(size);
        assertTrue("MESSAGE of " + size + " bytes must be answered, got no response",
                AssertUntil.assertUntil(shootist.getResponseAssertion(), TIMEOUT));
        assertEquals(Response.OK, shootist.getFirstStatus());
        assertEquals("MESSAGE must reach the UAS", 1, shootme.getReceivedMessages().size());
        assertNoIo();
    }

    /** never reaches the UAS and no 200 (a 513 would still pass) */
    private void assertNotDelivered(int size) throws Exception {
        send(size);
        assertFalse("MESSAGE of " + size + " bytes must not reach the UAS",
                AssertUntil.assertUntil(shootme.getMessageReceivedAssertion(), TIMEOUT));
        assertNotEquals("MESSAGE of " + size + " bytes must not be answered 200", Response.OK,
                shootist.getFirstStatus());
        assertNoIo();
    }

    /** send and check exactly size bytes went out */
    private void send(int size) throws Exception {
        shootist.sendMessage(size);
        assertTrue("MESSAGE must be sent", AssertUntil.assertUntil(shootist.getSentAssertion(), TIMEOUT));
        assertEquals("message size", size, shootist.getSentSize());
    }

    private void assertNoIo() {
        assertFalse("IOException on UAC", shootist.isIoExceptionSeen());
        assertFalse("IOException on UAS", shootme.isIoExceptionSeen());
    }

    static Properties properties(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }
}
