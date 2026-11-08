package test.b2b.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Requests on unlinked dialogs get 481. 
 */
public class UnlinkedDialogRejectedB2bTest extends TestCase {

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
        // no BYE in this test
        alice.byeDelay = 600000;
        b2bua = new BackToBackUserAgent(b2bPort1, b2bPort2, bobPort);
        bob = new Shootme(bobPort);
        bob.okDelay = 200;
    }

    public void testRequestOnUnlinkedDialogRejected() throws Exception {
        alice.sendInvite();

        assertTrue("Call should establish through the B2BUA",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isInviteOkSeen() && bob.isAckSeen();
                    }
                }, TIMEOUT));

        b2bua.unlinkAllDialogs();
        bob.sendReInvite();

        assertTrue("Re-INVITE on the unlinked dialog must be answered 481",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bob.getReInviteFinalStatus() == 481;
                    }
                }, TIMEOUT));
        assertEquals("B2BUA should have rejected exactly one unlinked request", 1,
                b2bua.getUnlinkedRequestsRejected());
        assertFalse("Nothing may be forwarded to the caller", alice.isReInviteReceived());
    }

    @Override
    public void tearDown() throws Exception {
        alice.stop();
        b2bua.stop();
        bob.stop();
    }
}
