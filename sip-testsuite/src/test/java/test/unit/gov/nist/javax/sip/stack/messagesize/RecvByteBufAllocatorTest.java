package test.unit.gov.nist.javax.sip.stack.messagesize;

import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import io.netty.channel.Channel;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Unset or bad NETTY_RCVBUF_ALLOCATOR(_SIZE): UDP and TCP channels keep Netty's own allocator,
 * nothing changes by default.
 */
public class RecvByteBufAllocatorTest {

    private static final String ALLOCATOR = "gov.nist.javax.sip.NETTY_RCVBUF_ALLOCATOR";
    private static final String ALLOCATOR_SIZE = "gov.nist.javax.sip.NETTY_RCVBUF_ALLOCATOR_SIZE";

    private final List<SipStackImpl> stacks = new ArrayList<SipStackImpl>();

    private Channel udp;
    private Channel tcp;

    @Before
    public void setUp() throws Exception {
        udp = new NioDatagramChannel();
        tcp = new NioSocketChannel();
    }

    @After
    public void tearDown() throws Exception {
        udp.unsafe().closeForcibly();
        tcp.unsafe().closeForcibly();
        for (SipStackImpl stack : stacks) {
            stack.stop();
        }
    }

    @Test
    public void testUnsetKeepsNettyDefaults() throws Exception {
        assertDefaultsKept();
    }

    /** bad values are logged and ignored */
    @Test
    public void testBadValuesKeepNettyDefaults() throws Exception {
        assertDefaultsKept(ALLOCATOR, "BIG", ALLOCATOR_SIZE, "0");
    }

    private void assertDefaultsKept(String... keyValues) throws Exception {
        RecvByteBufAllocator udpDefault = udp.config().getRecvByteBufAllocator();
        RecvByteBufAllocator tcpDefault = tcp.config().getRecvByteBufAllocator();

        Properties properties = MessageSizeTest.properties(keyValues);
        properties.setProperty("javax.sip.STACK_NAME", "allocator-" + stacks.size());
        SipStackImpl stack = new SipStackImpl(properties);
        stacks.add(stack);
        NettyMessageProcessorFactory.configureRecvByteBufAllocator(udp, stack);
        NettyMessageProcessorFactory.configureRecvByteBufAllocator(tcp, stack);

        assertSame(udpDefault, udp.config().getRecvByteBufAllocator());
        assertSame(tcpDefault, tcp.config().getRecvByteBufAllocator());
    }
}
