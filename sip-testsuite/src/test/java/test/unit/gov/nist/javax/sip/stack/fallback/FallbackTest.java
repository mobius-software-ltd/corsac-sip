package test.unit.gov.nist.javax.sip.stack.fallback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Properties;

import javax.sip.header.CSeqHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * UDP to TCP fallback over MAX_UDP_MESSAGE_SIZE: the stack refuses the message with MessageToLong, the UA
 * sends it again over TCP. INVITE or 200 over the limit, with and without dialogs.
 */
public class FallbackTest {

    private static final int TIMEOUT = 10000;
    /** BYE server transactions linger 64*T1 over UDP */
    private static final int CLEANUP = 40000;
    private static final int BODY_SIZE = 400;
    /** only an INVITE with a body is over */
    private static final int REQUEST_LIMIT = 450;
    /** only a 200 with a body is over */
    private static final int RESPONSE_LIMIT = 700;

    private static final String MAX_UDP_MESSAGE_SIZE = "gov.nist.javax.sip.MAX_UDP_MESSAGE_SIZE";

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

    /** INVITE over 450 bytes, again over TCP. The UAC ends up with two SIPDialogs for the call. */
    @Test(timeout = 120000)
    public void testFallback() throws Exception {
        start(true, REQUEST_LIMIT);
        shootist.inviteBodySize = BODY_SIZE;

        assertInviteFallback();
        assertCleanUp(2, 1);
    }

    /** INVITE over 450 bytes, again over TCP, no dialogs. */
    @Test(timeout = 120000)
    public void testFallbackTransactional() throws Exception {
        start(false, REQUEST_LIMIT);
        shootist.inviteBodySize = BODY_SIZE;

        assertInviteFallback();
        assertCleanUp(0, 0);
    }

    /** 200 over 700 bytes, again over TCP. */
    @Test(timeout = 120000)
    public void testFallbackOnOKResponse() throws Exception {
        start(true, RESPONSE_LIMIT);
        shootme.okBodySize = BODY_SIZE;

        assertOkFallback();
        assertCleanUp(1, 1);
    }

    /** 200 over 700 bytes, again over TCP, no dialogs. */
    @Test(timeout = 120000)
    public void testFallbackOnOKResponseTransactional() throws Exception {
        start(false, RESPONSE_LIMIT);
        shootme.okBodySize = BODY_SIZE;

        assertOkFallback();
        assertCleanUp(0, 0);
    }

    private void start(boolean dialogs, int maxUdpMessageSize) {
        Properties properties = properties(MAX_UDP_MESSAGE_SIZE, Integer.toString(maxUdpMessageSize));
        shootme = new Shootme(shootmePort, dialogs, properties);
        shootist = new Shootist(shootistPort, shootmePort, dialogs, properties);
    }

    /** responses come back on the INVITE's connection, ACK and BYE go to the Contact over UDP */
    private void assertInviteFallback() throws Exception {
        call();
        assertEquals("UAC fallbacks", 1, shootist.getFallbacks());
        assertEquals("UAS fallbacks", 0, shootme.getFallbacks());
        assertEquals(Arrays.asList("183 INVITE TCP", "200 INVITE TCP", "BYE UDP"), shootist.getReceived());
        assertEquals(Arrays.asList("INVITE TCP", "ACK UDP", "200 BYE UDP"), shootme.getReceived());
    }

    private void assertOkFallback() throws Exception {
        call();
        assertEquals("UAC fallbacks", 0, shootist.getFallbacks());
        assertEquals("UAS fallbacks", 1, shootme.getFallbacks());
        assertEquals(Arrays.asList("183 INVITE UDP", "200 INVITE TCP", "BYE UDP"), shootist.getReceived());
        assertEquals(Arrays.asList("INVITE UDP", "ACK UDP", "200 BYE UDP"), shootme.getReceived());
    }

    private void call() throws Exception {
        shootist.sendInvite();
        assertTrue("UAC must see the 200 and the BYE", AssertUntil.assertUntil(shootist.getCallAssertion(), TIMEOUT));
        assertTrue("UAS must see the ACK and the 200 to its BYE",
                AssertUntil.assertUntil(shootme.getCallAssertion(), TIMEOUT));
    }

    private void assertCleanUp(int uacDialogs, int uasDialogs) throws Exception {
        assertEquals("errors on UAC", 0, shootist.getErrors());
        assertEquals("errors on UAS", 0, shootme.getErrors());
        assertTrue("transactions or dialogs left on UAC", AssertUntil.assertUntil(shootist.getCleanAssertion(), CLEANUP));
        assertTrue("transactions or dialogs left on UAS", AssertUntil.assertUntil(shootme.getCleanAssertion(), CLEANUP));
        assertEquals("dialogs terminated on UAC", uacDialogs, shootist.getDialogsTerminated());
        assertEquals("dialogs terminated on UAS", uasDialogs, shootme.getDialogsTerminated());
    }

    static Properties properties(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    static String body(int size) {
        char[] body = new char[size];
        Arrays.fill(body, 'a');
        return new String(body);
    }

    /** "INVITE TCP", "200 BYE UDP": the sender rewrites the top Via to the transport it used */
    static String describe(Message message) {
        String transport = ((ViaHeader) message.getHeader(ViaHeader.NAME)).getTransport();
        if (message instanceof Request) {
            return ((Request) message).getMethod() + " " + transport;
        }
        return ((Response) message).getStatusCode() + " " + ((CSeqHeader) message.getHeader(CSeqHeader.NAME)).getMethod()
                + " " + transport;
    }
}
