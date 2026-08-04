package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/*
 * User sends REGISTER from 2 addresses
 * 200 one
 * CANCEL the other
 */
public class UserMultipleRegistrationsForkTest extends TestCase {

    private static final int TIMEOUT = 25000;
    private static final String USER = "bob";
    private static final String PASSWORD = "supersecret";

    private Registree registreeA;
    private Registree registreeB;
    private Registrar registrar;
    private Shootist caller;
    private TestProxy proxy;
    private Shootme bindingA;
    private Shootme bindingB;

    @Override
    public void setUp() throws Exception {
        int registreeAPort = NetworkPortAssigner.retrieveNextPort();
        int registreeBPort = NetworkPortAssigner.retrieveNextPort();
        int registrarPort = NetworkPortAssigner.retrieveNextPort();
        int callerPort = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int bindingAPort = NetworkPortAssigner.retrieveNextPort();
        int bindingBPort = NetworkPortAssigner.retrieveNextPort();

        registrar = new Registrar(registrarPort, "test.mobius.local", PASSWORD);
        registreeA = new Registree(registreeAPort, registrarPort, USER, PASSWORD);
        registreeA.contactPort = bindingAPort;
        registreeB = new Registree(registreeBPort, registrarPort, USER, PASSWORD);
        registreeB.contactPort = bindingBPort;

        caller = new Shootist(callerPort, proxyPort);
        caller.byeDelay = 500;
        proxy = new TestProxy(proxyPort, TestProxy.Mode.PARALLEL, new int[0]);
        proxy.locationService = registrar;
        proxy.locationServiceUser = USER;
        proxy.cancelRemainingBranchesOn2xx = true;

        bindingA = new Shootme(bindingAPort, Shootme.Behavior.ANSWER, 300);
        bindingB = new Shootme(bindingBPort, Shootme.Behavior.RING_ONLY, 0);
    }

    public void testUserMultipleRegistrationsFork() throws Exception {
        registreeA.sendRegister();
        registreeB.sendRegister();

        assertTrue("Both registrations should be challenged and accepted",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return registreeA.isRegistered() && registreeB.isRegistered();
                    }
                }, TIMEOUT));
        assertEquals("Location service should hold both bindings", 2, registrar.getBindings(USER).size());

        caller.sendInvite();

        assertTrue("Caller should complete the call with the answering binding",
                AssertUntil.assertUntil(caller.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Answering binding should see the complete call",
                AssertUntil.assertUntil(bindingA.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Losing binding should be CANCELed by the proxy",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return bindingB.isCancelSeen();
                    }
                }, TIMEOUT));
        assertTrue("INVITE should have been forked onto both registered bindings",
                bindingA.isInviteSeen() && bindingB.isInviteSeen());
        assertEquals("Proxy should have canceled exactly one branch", 1, proxy.getCanceledBranches());
        assertFalse("The canceled branch's 487 must not reach the caller",
                caller.isRequestTerminatedSeen());
        assertTrue("No other failure response may reach the caller",
                caller.getUnexpectedFinalResponses().isEmpty());
    }

    @Override
    public void tearDown() throws Exception {
        registreeA.stop();
        registreeB.stop();
        registrar.stop();
        caller.stop();
        proxy.stop();
        bindingA.stop();
        bindingB.stop();
    }
}
