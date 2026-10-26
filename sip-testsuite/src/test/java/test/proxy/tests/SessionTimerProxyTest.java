package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Session timers per RFC 4028: 422 with Min-SE, retry, refresh reINVITE
 * through the proxy.
 */
public class SessionTimerProxyTest extends TestCase {

    private static final int TIMEOUT = 20000;

    private Shootist caller;
    private TestProxy proxy;
    private Shootme callee;

    @Override
    public void setUp() throws Exception {
        int callerPort = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int calleePort = NetworkPortAssigner.retrieveNextPort();

        caller = new Shootist(callerPort, proxyPort);
        caller.sessionExpires = 30;
        caller.refreshDelay = 1000;
        caller.byeDelay = 500;
        proxy = new TestProxy(proxyPort, TestProxy.Mode.SEQUENTIAL, new int[] { calleePort });
        proxy.minSE = 90;
        callee = new Shootme(calleePort, Shootme.Behavior.ANSWER, 200);
    }

    public void testSessionTimerNegotiationAndRefresh() throws Exception {
        caller.sendInvite();

        assertTrue("Caller should get 422, retry with Min-SE, refresh the session and complete the call",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return caller.isRejected422Seen() && caller.isOkSeen() && caller.isRefreshOkSeen()
                                && caller.isByeOkSeen();
                    }
                }, TIMEOUT));
        assertTrue("Proxy should have rejected the first interval", proxy.isRejected422());
        assertEquals("422 should announce the proxy minimum", 90, caller.getMinSEOffered());
        assertTrue("Callee should see the session refresh re-INVITE",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return callee.isReInviteSeen() && callee.isByeSeen();
                    }
                }, TIMEOUT));
        assertTrue("Refresh must have traversed the proxy", proxy.getSessionRefreshesForwarded() >= 1);
    }

    @Override
    public void tearDown() throws Exception {
        caller.stop();
        proxy.stop();
        callee.stop();
    }
}
