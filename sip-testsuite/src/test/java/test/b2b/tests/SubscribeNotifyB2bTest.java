package test.b2b.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Subscribe/Notify through the B2B, full lifecycle.
 */
public class SubscribeNotifyB2bTest extends TestCase {

    private static final int TIMEOUT = 20000;

    private Subscriber alice;
    private SubscribeNotifyBackToBackUserAgent b2bua;
    private Notifier bob;

    @Override
    public void setUp() throws Exception {
        int alicePort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort1 = NetworkPortAssigner.retrieveNextPort();
        int b2bPort2 = NetworkPortAssigner.retrieveNextPort();
        int bobPort = NetworkPortAssigner.retrieveNextPort();

        alice = new Subscriber(alicePort, b2bPort1);
        b2bua = new SubscribeNotifyBackToBackUserAgent(b2bPort1, b2bPort2, bobPort);
        bob = new Notifier(bobPort, false);
    }

    public void testSubscribeNotifyLifecycle() throws Exception {
        alice.sendSubscribe();

        assertTrue("Subscriber should see the SUBSCRIBE 200, an active NOTIFY, the unsubscribe 200 and the "
                + "terminating NOTIFY", AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isSubscribeOkSeen() && alice.getActiveNotifyCount() >= 1
                                && alice.isUnsubscribeOkSeen() && alice.isTerminatedNotifySeen();
                    }
                }, TIMEOUT));
        assertTrue("Notifier should see the subscription, its termination and the NOTIFY 200s",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bob.isSubscribeSeen() && bob.isUnsubscribeSeen() && bob.getNotifyOkCount() >= 2;
                    }
                }, TIMEOUT));
        assertTrue("B2BUA should have relayed at least two NOTIFYs", b2bua.getNotifiesRelayed() >= 2);
        assertTrue("B2BUA should have relayed the SUBSCRIBE responses",
                b2bua.getSubscribeResponsesRelayed() >= 2);
        assertTrue("B2BUA should have relayed a terminated subscription state",
                b2bua.getRelayedSubscriptionStates().contains("terminated"));
    }

    @Override
    public void tearDown() throws Exception {
        alice.stop();
        b2bua.stop();
        bob.stop();
    }
}
