package test.b2b.tests;

import junit.framework.TestCase;
import test.proxy.tests.TestProxy;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Downstream fork, two 200s arrive: first one wins, the loser gets ACK+BYE.
 */
public class ForkedAcceptOneB2bTest extends TestCase {

    private static final int TIMEOUT = 25000;

    private Shootist alice;
    private BackToBackUserAgent b2bua;
    private TestProxy forkingProxy;
    private test.proxy.tests.Shootme bob;
    private test.proxy.tests.Shootme carol;

    @Override
    public void setUp() throws Exception {
        int alicePort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort1 = NetworkPortAssigner.retrieveNextPort();
        int b2bPort2 = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int bobPort = NetworkPortAssigner.retrieveNextPort();
        int carolPort = NetworkPortAssigner.retrieveNextPort();

        alice = new Shootist(alicePort, b2bPort1);
        alice.byeDelay = 3000;
        b2bua = new BackToBackUserAgent(b2bPort1, b2bPort2, proxyPort);
        b2bua.acceptOneForkedResponse = true;
        forkingProxy = new TestProxy(proxyPort, TestProxy.Mode.PARALLEL, new int[] { bobPort, carolPort });
        bob = new test.proxy.tests.Shootme(bobPort, test.proxy.tests.Shootme.Behavior.ANSWER, 100);
        carol = new test.proxy.tests.Shootme(carolPort, test.proxy.tests.Shootme.Behavior.ANSWER, 1500);
    }

    public void testAcceptOneTerminateOther() throws Exception {
        alice.sendInvite();

        assertTrue("The B2BUA should terminate exactly one extra forked dialog",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return b2bua.getForkedDialogsTerminated() == 1;
                    }
                }, TIMEOUT));
        assertTrue("Caller should complete a single call",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isInviteOkSeen() && alice.isByeOkSeen();
                    }
                }, TIMEOUT));
        assertTrue("Both forked targets should be answered (ACK) and torn down (BYE)",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bob.isAckSeen() && bob.isByeSeen() && carol.isAckSeen() && carol.isByeSeen();
                    }
                }, TIMEOUT));
        assertEquals("Caller must see exactly one answered dialog", 1, alice.getOkDialogCount());
    }

    @Override
    public void tearDown() throws Exception {
        alice.stop();
        b2bua.stop();
        forkingProxy.stop();
        bob.stop();
        carol.stop();
    }
}
