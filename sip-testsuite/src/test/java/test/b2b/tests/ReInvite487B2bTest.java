package test.b2b.tests;

import javax.sip.message.Response;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * reINVITE from the callee rejected with 487, dialog must survive.
 */
public class ReInvite487B2bTest extends TestCase {

    private static final int TIMEOUT = 20000;

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
        alice.answerReInviteWith = Response.REQUEST_TERMINATED;
        alice.byeDelay = 2000;
        b2bua = new BackToBackUserAgent(b2bPort1, b2bPort2, bobPort);
        bob = new Shootme(bobPort);
        bob.okDelay = 200;
        bob.reInviteAfterAck = 400;
    }

    public void testReInviteFromCalleeRejectedWith487() throws Exception {
        alice.sendInvite();

        assertTrue("Callee's re-INVITE should come back rejected with 487 through the B2BUA",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isReInviteReceived()
                                && bob.getReInviteFinalStatus() == Response.REQUEST_TERMINATED;
                    }
                }, TIMEOUT));
        assertTrue("Dialog must survive the rejected re-INVITE and close with the caller's BYE",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isInviteOkSeen() && alice.isByeOkSeen() && bob.isAckSeen() && bob.isByeSeen();
                    }
                }, TIMEOUT));
    }

    @Override
    public void tearDown() throws Exception {
        alice.stop();
        b2bua.stop();
        bob.stop();
    }
}
