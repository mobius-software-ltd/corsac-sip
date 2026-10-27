package test.unit.gov.nist.javax.sip.stack.tx.finalresponse.terminatedtx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import javax.sip.TransactionState;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.tx.finalresponse.terminatedtx.Shootme.Flow;

/**
 * Attempt to send 2 finals in answer to REGISTER, automatic dialogs are off
 * Only 1 is supposed to go out, second should get an exception event and a log
 */
public class TerminatedTransactionResponseSentTest {

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

    /** 200, then a 504 once the stx is Completed: the 504 is refused. */
    @Test(timeout = 30000)
    public void testNormal() throws Exception {
        register(Flow.NORMAL);
        assertTrue("stx must reach Completed once the 200 is out, " + shootme.describe(),
                AssertUntil.assertUntil(shootme.getCompletedAssertion(), TIMEOUT));
        shootme.sendTimeout();
        assertEquals("stx state when the 504 is sent, " + shootme.describe(), TransactionState.COMPLETED,
                shootme.getStateBeforeTimeout());
        assertOneFinal(Response.OK, Response.SERVER_TIMEOUT);
    }

    /** 200 and 504 queued, stx terminated before the worker runs them: the 504 is refused. */
    @Test(timeout = 30000)
    public void testTerminated() throws Exception {
        register(Flow.TERMINATED);
        assertOneFinal(Response.OK, Response.SERVER_TIMEOUT);
    }

    /** Two 504s queued back to back: the second is refused. */
    @Test(timeout = 30000)
    public void testDouble504() throws Exception {
        register(Flow.DOUBLE_504);
        assertOneFinal(Response.SERVER_TIMEOUT, Response.SERVER_TIMEOUT);
    }

    /** REGISTER in, the UAS runs its flow */
    private void register(Flow flow) throws Exception {
        shootme = new Shootme(shootmePort, UDP, flow);
        shootist = new Shootist(shootistPort, shootmePort, UDP);

        shootist.sendRegister();
        assertTrue("UAS must get the REGISTER and run its flow",
                AssertUntil.assertUntil(shootme.getFlowDoneAssertion(), TIMEOUT));
    }

    /** any exception fails the test as the cause, then one final on the wire and one refused */
    private void assertOneFinal(int statusCode, int refusedStatusCode) throws Exception {
        Thread.sleep(QUIET);
        List<String> exceptions = shootme.getExceptions();
        if (!exceptions.isEmpty()) {
            throw new AssertionError("exceptions " + exceptions + ", " + shootme.describe(), shootme.getFirstException());
        }

        List<String> finals = shootist.getRegisterFinals();
        assertEquals("finals to the REGISTER on the wire " + finals + ", " + shootme.describe(), 1, finals.size());
        assertEquals("the UAC must get the " + statusCode + ", on its REGISTER",
                statusCode + " " + shootist.getRegisterBranch(), finals.get(0));
        assertEquals("the app must be told the stack refused the " + refusedStatusCode + ", " + shootme.describe(),
                Collections.singletonList(refusedStatusCode), shootme.getRefused());
    }
}
