package test.unit.gov.nist.javax.sip.stack.registerflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * Checks double sends in non-dialog transactions in every possible UDP and TCP case.
 * REGISTER, dialog support off, 200 then 504 on the same stx: only the 200 may go out, and the
 * stx must not be left behind. A case is UDP or TCP, stx from the delivered REGISTER or from a
 * clone of it, taken in processRequest or later from another thread.
 */
public class RegisterFlowTest {

    private static final String UDP = "udp";
    private static final String TCP = "tcp";

    private static final int TIMEOUT = 10000;
    /** room for a second final to show up, well short of Timer J */
    private static final long QUIET = 3000;
    /** Timer J (64*T1 = 32 s) plus margin */
    private static final int TRANSACTION_CLEANUP = 40000;

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

    /** UDP, stx from a clone of the REGISTER: no double send. */
    @Test(timeout = 30000)
    public void testFlow() throws Exception {
        assertOnlyOkOnTheWire(UDP, true, false);
    }

    /** UDP, stx from a clone of the REGISTER: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeak() throws Exception {
        assertNoTransactionLeft(UDP, true, false);
    }

    /** UDP, stx from a clone of the REGISTER, taken later: no double send. */
    @Test(timeout = 30000)
    public void testFlowCloneLater() throws Exception {
        assertOnlyOkOnTheWire(UDP, true, true);
    }

    /** UDP, stx from a clone of the REGISTER, taken later: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakCloneLater() throws Exception {
        assertNoTransactionLeft(UDP, true, true);
    }

    /** UDP, stx from the delivered REGISTER: no double send. */
    @Test(timeout = 30000)
    public void testFlowDelivered() throws Exception {
        assertOnlyOkOnTheWire(UDP, false, false);
    }

    /** UDP, stx from the delivered REGISTER: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakDelivered() throws Exception {
        assertNoTransactionLeft(UDP, false, false);
    }

    /** UDP, stx from the delivered REGISTER, taken later: no double send. */
    @Test(timeout = 30000)
    public void testFlowDeliveredLater() throws Exception {
        assertOnlyOkOnTheWire(UDP, false, true);
    }

    /** UDP, stx from the delivered REGISTER, taken later: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakDeliveredLater() throws Exception {
        assertNoTransactionLeft(UDP, false, true);
    }

    /** TCP, stx from a clone of the REGISTER: no double send. */
    @Test(timeout = 30000)
    public void testFlowTcp() throws Exception {
        assertOnlyOkOnTheWire(TCP, true, false);
    }

    /** TCP, stx from a clone of the REGISTER: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakTcp() throws Exception {
        assertNoTransactionLeft(TCP, true, false);
    }

    /** TCP, stx from a clone of the REGISTER, taken later: no double send. */
    @Test(timeout = 30000)
    public void testFlowTcpCloneLater() throws Exception {
        assertOnlyOkOnTheWire(TCP, true, true);
    }

    /** TCP, stx from a clone of the REGISTER, taken later: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakTcpCloneLater() throws Exception {
        assertNoTransactionLeft(TCP, true, true);
    }

    /** TCP, stx from the delivered REGISTER: no double send. */
    @Test(timeout = 30000)
    public void testFlowTcpDelivered() throws Exception {
        assertOnlyOkOnTheWire(TCP, false, false);
    }

    /** TCP, stx from the delivered REGISTER: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakTcpDelivered() throws Exception {
        assertNoTransactionLeft(TCP, false, false);
    }

    /** TCP, stx from the delivered REGISTER, taken later: no double send. */
    @Test(timeout = 30000)
    public void testFlowTcpDeliveredLater() throws Exception {
        assertOnlyOkOnTheWire(TCP, false, true);
    }

    /** TCP, stx from the delivered REGISTER, taken later: no stx left behind. */
    @Test(timeout = 90000)
    public void testLeakTcpDeliveredLater() throws Exception {
        assertNoTransactionLeft(TCP, false, true);
    }

    /** one final on the wire, the 200, on our REGISTER */
    private void assertOnlyOkOnTheWire(String transport, boolean clone, boolean later) throws Exception {
        runFlow(transport, clone, later);
        Thread.sleep(QUIET);
        List<String> finals = shootist.getRegisterFinals();
        assertEquals("finals to the REGISTER on the wire " + finals + ", " + shootme.describe(), 1, finals.size());
        assertEquals("the UE must get the 200, on its REGISTER", "200 " + shootist.getRegisterBranch(), finals.get(0));
    }

    /** nothing left in transaction tables after Timer J */
    private void assertNoTransactionLeft(String transport, boolean clone, boolean later) throws Exception {
        runFlow(transport, clone, later);
        boolean clean = AssertUntil.assertUntil(shootme.getNoTransactionsAssertion(), TRANSACTION_CLEANUP);
        assertTrue("transactions left on the I-CSCF, client/server " + shootme.getClientTransactionTableSize() + "/"
                + shootme.getServerTransactionTableSize() + ", " + shootme.describe(), clean);
    }

    /** REGISTER from client; any exception in it fails the test as the cause */
    private void runFlow(String transport, boolean clone, boolean later) throws Exception {
        shootme = new Shootme(shootmePort, transport, clone, later);
        shootist = new Shootist(shootistPort, shootmePort, transport);

        shootist.sendRegister();
        assertTrue("I-CSCF must get the REGISTER and run its flow",
                AssertUntil.assertUntil(shootme.getFlowDoneAssertion(), TIMEOUT));
        List<String> exceptions = shootme.getExceptions();
        if (!exceptions.isEmpty()) {
            throw new AssertionError("exceptions in the flow " + exceptions + ", " + shootme.describe(),
                    shootme.getFirstException());
        }
    }
}
