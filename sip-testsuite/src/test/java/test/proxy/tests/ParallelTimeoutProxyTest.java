package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * proxy timeout CANCELs both branches
 * the best final (487) goes upstream
 */
public class ParallelTimeoutProxyTest extends TestCase {

    private static final int TIMEOUT = 20000;

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
        proxy = new TestProxy(proxyPort, TestProxy.Mode.PARALLEL, new int[] { bobPort, carolPort });
        proxy.branchTimeout = 2000;
        bob = new Shootme(bobPort, Shootme.Behavior.RING_ONLY, 0);
        carol = new Shootme(carolPort, Shootme.Behavior.RING_ONLY, 0);
    }

    public void testParallelForkTimeout() throws Exception {
        caller.sendInvite();

        assertTrue("Both ringing branches should be CANCELed and the caller should see the best final (487)",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return caller.isRequestTerminatedSeen() && bob.isCancelSeen() && carol.isCancelSeen();
                    }
                }, TIMEOUT));
        assertTrue("Both branches should have received the INVITE", bob.isInviteSeen() && carol.isInviteSeen());
        assertEquals("Proxy should have canceled both branches", 2, proxy.getCanceledBranches());
        assertFalse("Caller should never see a 200 OK", caller.isOkSeen());
    }

    @Override
    public void tearDown() throws Exception {
        caller.stop();
        proxy.stop();
        bob.stop();
        carol.stop();
    }
}
