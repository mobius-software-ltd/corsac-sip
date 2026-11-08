package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Recursion on 3xx: callee redirects,
 * proxy finds the contact on it's own 
 */
public class RecursionProxyTest extends TestCase {

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
        proxy = new TestProxy(proxyPort, TestProxy.Mode.RECURSE, new int[] { bobPort });
        bob = new Shootme(bobPort, Shootme.Behavior.REDIRECT, 0);
        bob.redirectPort = carolPort;
        carol = new Shootme(carolPort, Shootme.Behavior.ANSWER, 200);
    }

    public void testRecursionOn302() throws Exception {
        caller.sendInvite();

        assertTrue("Caller should complete the call with the redirect target",
                AssertUntil.assertUntil(caller.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Redirect target should see the complete call",
                AssertUntil.assertUntil(carol.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Redirecting target should have been tried", bob.isInviteSeen());
        assertEquals("Proxy should have recursed exactly once", 1, proxy.getRedirectsFollowed());
        assertTrue("The 302 must not reach the caller - the proxy handles it on its own",
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
