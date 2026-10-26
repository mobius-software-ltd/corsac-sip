package test.b2b.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Reliable 183 + PRACK through the B2B. BYE from the caller side.
 */
public class PrackByeB2bTest extends TestCase {

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
        alice.requireReliableProvisionalResponse = true;
        alice.byeDelay = 500;
        b2bua = new BackToBackUserAgent(b2bPort1, b2bPort2, bobPort);
        bob = new Shootme(bobPort);
        bob.sendReliableProvisionalResponse = true;
        bob.okDelay = 300;
    }

    public void testPrackCallTornDownByCaller() throws Exception {
        alice.sendInvite();

        assertTrue("Caller should see the reliable 183, a confirmed PRACK, the 200 OK and the BYE response",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.getReliableProvisionalCount() >= 1 && alice.getPrackOkCount() >= 1
                                && alice.isInviteOkSeen() && alice.isByeOkSeen();
                    }
                }, TIMEOUT));
        assertTrue("Callee should see the PRACK, the ACK and the caller's BYE",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bob.getPrackReceivedCount() >= 1 && bob.isAckSeen() && bob.isByeSeen();
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
