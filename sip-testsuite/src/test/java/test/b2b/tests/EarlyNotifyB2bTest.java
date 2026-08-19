package test.b2b.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * NOTIFY reaches us before answer to SUBSCRIBE 
 */
public class EarlyNotifyB2bTest extends TestCase {

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
        bob = new Notifier(bobPort, true);
    }

    public void testEarlyNotifyReachesSubscriberBefore200() throws Exception {
        alice.sendSubscribe();

        assertTrue("Subscriber should complete the subscription lifecycle",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return alice.isSubscribeOkSeen() && alice.getActiveNotifyCount() >= 1
                                && alice.isUnsubscribeOkSeen() && alice.isTerminatedNotifySeen();
                    }
                }, TIMEOUT));
        assertTrue("The first NOTIFY must arrive at the subscriber before the SUBSCRIBE 2xx",
                alice.isFirstNotifyBeforeSubscribeOk());
        assertTrue("Notifier should see its early NOTIFY acknowledged",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bob.getNotifyOkCount() >= 2 && bob.isUnsubscribeSeen();
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
