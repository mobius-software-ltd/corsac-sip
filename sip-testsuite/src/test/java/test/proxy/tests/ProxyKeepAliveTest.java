package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;


/**
 * pings are to be answered by proxy itself
 * pings must NOT reach the other leg
 * call must survive the idle period 
 */
public class ProxyKeepAliveTest extends TestCase {

    private static final int TIMEOUT = 25000;
    private static final long KEEPALIVE_INTERVAL_MS = 400;

    private Shootist caller;
    private TestProxy proxy;
    private Shootme callee;

    @Override
    public void setUp() throws Exception {
        int callerPort = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int calleePort = NetworkPortAssigner.retrieveNextPort();

        caller = new Shootist(callerPort, proxyPort);
        // session timer is just the vehicle for the post-idle reINVITE
        caller.sessionExpires = 120;
        caller.refreshDelay = 4000;
        caller.byeDelay = 500;
        proxy = new TestProxy(proxyPort, TestProxy.Mode.SEQUENTIAL, new int[] { calleePort });
        callee = new Shootme(calleePort, Shootme.Behavior.ANSWER, 200);
    }

    public void testProxyKeepAlive() throws Exception {
        caller.startKeepAlive(KEEPALIVE_INTERVAL_MS);

        // pings must already flow before any call exists
        assertTrue("Keepalives should be answered by the proxy",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return caller.getKeepAliveOkCount() >= 2 && proxy.getKeepAlivesAnswered() >= 2;
                    }
                }, TIMEOUT));

        // ---- establish a call through the proxy ----
        caller.sendInvite();
        assertTrue("Call should establish", AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return caller.isOkSeen() && callee.isAckSeen();
            }
        }, TIMEOUT));

        // ---- keepalive watch: pings must keep flowing while nothing else is on the wire ----
        final int answeredBeforeIdle = proxy.getKeepAlivesAnswered();
        final int okBeforeIdle = caller.getKeepAliveOkCount();
        assertTrue("Keepalives should keep flowing through the idle period",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return proxy.getKeepAlivesAnswered() >= answeredBeforeIdle + 5
                                && caller.getKeepAliveOkCount() >= okBeforeIdle + 5;
                    }
                }, TIMEOUT));

        // ---- path must have survived call + idle: refresh reINVITE still routes ----
        assertTrue("Session refresh re-INVITE should route after the idle window",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return caller.isRefreshOkSeen() && callee.isReInviteSeen() && caller.isByeOkSeen()
                                && callee.isByeSeen();
                    }
                }, TIMEOUT));
        assertTrue("Refresh must have traversed the proxy", proxy.getSessionRefreshesForwarded() >= 1);
        assertFalse("OPTIONS keepalive must never be forwarded to the far end", callee.isOptionsReceived());
    }

    @Override
    public void tearDown() throws Exception {
        caller.stop();
        proxy.stop();
        callee.stop();
    }
}
