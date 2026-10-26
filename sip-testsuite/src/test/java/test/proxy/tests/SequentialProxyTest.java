package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * sequential proxy search
 * first answers with 486
 * second with 200
 */
public class SequentialProxyTest extends TestCase {

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
        caller.byeDelay = 500;
        proxy = new TestProxy(proxyPort, TestProxy.Mode.SEQUENTIAL, new int[] { bobPort, carolPort });
        bob = new Shootme(bobPort, Shootme.Behavior.BUSY, 0);
        carol = new Shootme(carolPort, Shootme.Behavior.ANSWER, 200);
    }

    public void testSequentialHunting() throws Exception {
        caller.sendInvite();

        assertTrue("Caller should complete the call with the second target",
                AssertUntil.assertUntil(caller.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Second target should see the complete call",
                AssertUntil.assertUntil(carol.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("First target should have been tried", bob.isInviteSeen());
        assertEquals("Proxy should have hunted past exactly one busy target", 1, proxy.getRejectedTargets());
        assertTrue("The 486 of the first target must not reach the caller",
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
