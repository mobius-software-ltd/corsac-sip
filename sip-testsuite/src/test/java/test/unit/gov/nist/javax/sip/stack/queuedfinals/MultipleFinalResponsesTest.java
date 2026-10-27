package test.unit.gov.nist.javax.sip.stack.queuedfinals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * 200 then a 504 queued one after another on a REGISTER, dialog support off, UDP
 * Should only let 200 through
 */
public class MultipleFinalResponsesTest {

    private static final String UDP = "udp";

    private static final int TIMEOUT = 10000;
    /** room for a second final to show up, well short of Timer J */
    private static final long QUIET = 3000;

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

    /** Both through the stx as the stack hands it out (Trying): no double send. */
    @Test(timeout = 30000)
    public void testQueued() throws Exception {
        shootme = new Shootme(shootmePort, UDP);
        assertOnlyOkOnTheWire();
    }

    /** Both through a stx set to unset (-1): no double send. */
    @Test(timeout = 30000)
    public void testUnset() throws Exception {
        shootme = new Shootme(shootmePort, UDP);
        shootme.unset = true;
        assertOnlyOkOnTheWire();
    }

    /** Both through the stx, terminated once both are queued: no double send. */
    @Test(timeout = 30000)
    public void testTerminated() throws Exception {
        shootme = new Shootme(shootmePort, UDP);
        shootme.terminateAfter = true;
        assertOnlyOkOnTheWire();
    }

    /** 200 through the stx, 504 through the provider: no double send. */
    @Test(timeout = 30000)
    public void testStateless504() throws Exception {
        shootme = new Shootme(shootmePort, UDP);
        shootme.timeoutStateless = true;
        assertOnlyOkOnTheWire();
    }

    /** 200 through the provider, 504 through the stx: no double send. */
    @Test(timeout = 30000)
    public void testStateless200() throws Exception {
        shootme = new Shootme(shootmePort, UDP);
        shootme.okStateless = true;
        assertOnlyOkOnTheWire();
    }

    /** Both through the provider, no stx claimed: no double send. */
    @Test(timeout = 30000)
    public void testStatelessBoth() throws Exception {
        shootme = new Shootme(shootmePort, UDP);
        shootme.okStateless = true;
        shootme.timeoutStateless = true;
        assertOnlyOkOnTheWire();
    }

    /** REGISTER in, 200 and 504 queued; any exception fails the test as the cause, then one final, the 200 */
    private void assertOnlyOkOnTheWire() throws Exception {
        shootist = new Shootist(shootistPort, shootmePort, UDP);

        shootist.sendRegister();
        assertTrue("UAS must get the REGISTER and queue both finals",
                AssertUntil.assertUntil(shootme.getFlowDoneAssertion(), TIMEOUT));
        List<String> exceptions = shootme.getExceptions();
        if (!exceptions.isEmpty()) {
            throw new AssertionError("exceptions " + exceptions + ", " + shootme.describe(), shootme.getFirstException());
        }

        Thread.sleep(QUIET);
        List<String> finals = shootist.getRegisterFinals();
        assertEquals("finals to the REGISTER on the wire " + finals + ", " + shootme.describe(), 1, finals.size());
        assertEquals("the UAC must get the 200, on its REGISTER", "200 " + shootist.getRegisterBranch(), finals.get(0));
    }
}
