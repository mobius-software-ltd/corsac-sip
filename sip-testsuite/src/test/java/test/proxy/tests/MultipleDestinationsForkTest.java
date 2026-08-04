package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Three destinations send the request
 * 1 - answers with a 200
 * 2 - gets CANCEL from proxy
 * 3 - timeout
 */
public class MultipleDestinationsForkTest extends TestCase {

    private static final int TIMEOUT = 25000;

    private Shootist caller;
    private TestProxy proxy;
    private Shootme dest1;
    private Shootme dest2;
    private Shootme dest3;

    @Override
    public void setUp() throws Exception {
        int callerPort = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int dest1Port = NetworkPortAssigner.retrieveNextPort();
        int dest2Port = NetworkPortAssigner.retrieveNextPort();
        int dest3Port = NetworkPortAssigner.retrieveNextPort();

        caller = new Shootist(callerPort, proxyPort);
        caller.byeDelay = 500;
        proxy = new TestProxy(proxyPort, TestProxy.Mode.PARALLEL,
                new int[] { dest1Port, dest2Port, dest3Port });
        proxy.setPerBranchTimeoutMs(dest3Port, 1500);
        proxy.cancelRemainingBranchesOn2xx = true;
        dest1 = new Shootme(dest1Port, Shootme.Behavior.ANSWER, 3000);
        dest2 = new Shootme(dest2Port, Shootme.Behavior.RING_ONLY, 0);
        dest3 = new Shootme(dest3Port, Shootme.Behavior.RING_ONLY, 0);
    }

    public void testMultipleDestinationsFork() throws Exception {
        caller.sendInvite();

        assertTrue("Third destination should be CANCELed by its own branch timeout",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return dest3.isCancelSeen();
                    }
                }, TIMEOUT));
        assertFalse("First destination must not have answered yet", caller.isOkSeen());

        assertTrue("First destination should answer and complete the call",
                AssertUntil.assertUntil(caller.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Winning destination should see the complete call",
                AssertUntil.assertUntil(dest1.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Second destination should be CANCELed once the winner answered",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return dest2.isCancelSeen();
                    }
                }, TIMEOUT));
        assertTrue("All three destinations should have been tried",
                dest1.isInviteSeen() && dest2.isInviteSeen() && dest3.isInviteSeen());
        assertEquals("Proxy should have canceled exactly two branches", 2, proxy.getCanceledBranches());
        assertFalse("The canceled branches' 487s must not reach the caller",
                caller.isRequestTerminatedSeen());
        assertTrue("No other failure response may reach the caller",
                caller.getUnexpectedFinalResponses().isEmpty());
    }

    @Override
    public void tearDown() throws Exception {
        caller.stop();
        proxy.stop();
        dest1.stop();
        dest2.stop();
        dest3.stop();
    }
}
