package test.b2b.tests;

import javax.sip.message.Response;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * pings are to be answered by B2B itself
 * pings must NOT reach the other leg
 * call must survive the idle period 
 */
public class B2bKeepAliveTest extends TestCase {

    private static final int TIMEOUT = 25000;
    private static final long KEEPALIVE_INTERVAL_MS = 400;

    private Shootist alice;
    private BackToBackUserAgent b2bua;
    private Shootme bob;

    @Override
    public void setUp() throws Exception {
        int alicePort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort1 = NetworkPortAssigner.retrieveNextPort();
        int b2bPort2 = NetworkPortAssigner.retrieveNextPort();
        int bobPort = NetworkPortAssigner.retrieveNextPort();

        alice = new Shootist(alicePort, b2bPort1);
        alice.byeDelay = 9000;
        b2bua = new BackToBackUserAgent(b2bPort1, b2bPort2, bobPort);
        bob = new Shootme(bobPort);
        bob.okDelay = 200;
    }

    public void testB2bKeepAlive() throws Exception {
        alice.startKeepAlive(KEEPALIVE_INTERVAL_MS);

        assertTrue("Keepalives should be answered by the B2BUA",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.getKeepAliveOkCount() >= 2 && b2bua.getKeepAlivesAnswered() >= 2;
                    }
                }, TIMEOUT));

        alice.sendInvite();
        assertTrue("Call should establish", AssertUntil.assertUntil(new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return alice.isInviteOkSeen() && bob.isAckSeen();
            }
        }, TIMEOUT));

        final int answeredBeforeIdle = b2bua.getKeepAlivesAnswered();
        final int okBeforeIdle = alice.getKeepAliveOkCount();
        assertTrue("Keepalives should keep flowing through the idle period",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return b2bua.getKeepAlivesAnswered() >= answeredBeforeIdle + 5
                                && alice.getKeepAliveOkCount() >= okBeforeIdle + 5;
                    }
                }, TIMEOUT));

        bob.sendReInvite();
        assertTrue("Post-idle re-INVITE should still bridge through the B2BUA",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isReInviteReceived() && bob.getReInviteFinalStatus() == Response.OK;
                    }
                }, TIMEOUT));
        assertTrue("Call should close normally with the caller's BYE",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isByeOkSeen() && bob.isByeSeen();
                    }
                }, TIMEOUT));
        assertFalse("OPTIONS keepalive must never be bridged to the far leg", bob.isOptionsReceived());
    }

    @Override
    public void tearDown() throws Exception {
        alice.stop();
        b2bua.stop();
        bob.stop();
    }
}
