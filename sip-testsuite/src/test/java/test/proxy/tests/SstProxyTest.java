package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * sequential search with SST(sequential session timeout)
 * First never answers and gets CANCEL
 * Second answers 200 
 */
public class SstProxyTest extends TestCase {

    private static final int TIMEOUT = 25000;

    private Shootist caller;
    private TestProxy proxy;
    private Shootme bob;
    private Shootme carol;

    @Override
    public void setUp() throws Exception {
        int callerPort = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int bobPort = NetworkPortAssigner.retrieveNextPort();
        int carolPort = NetworkPortAssigner.retrieveNextPort();

        caller = new Shootist(callerPort, proxyPort);
        caller.byeDelay = 500;
        proxy = new TestProxy(proxyPort, TestProxy.Mode.SEQUENTIAL, new int[] { bobPort, carolPort });
        proxy.branchTimeout = 2000;
        bob = new Shootme(bobPort, Shootme.Behavior.RING_ONLY, 0);
        carol = new Shootme(carolPort, Shootme.Behavior.ANSWER, 200);
    }

    public void testSequentialSearchTimeout() throws Exception {
        caller.sendInvite();

        assertTrue("Ringing branch should be CANCELed and the call should complete with the second target",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bob.isCancelSeen() && caller.isOkSeen() && caller.isByeOkSeen();
                    }
                }, TIMEOUT));
        assertTrue("Second target should see the complete call",
                AssertUntil.assertUntil(carol.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("First target should have been tried and canceled", bob.isInviteSeen() && bob.isCancelSeen());
        assertEquals("Proxy should have canceled exactly one branch", 1, proxy.getCanceledBranches());
        assertFalse("The canceled branch's 487 must not reach the caller", caller.isRequestTerminatedSeen());
        assertTrue("No other failure response may reach the caller",
                caller.getUnexpectedFinalResponses().isEmpty());
    }

    @Override
    public void tearDown() throws Exception {
        caller.stop();
        proxy.stop();
        bob.stop();
        carol.stop();
    }
}
