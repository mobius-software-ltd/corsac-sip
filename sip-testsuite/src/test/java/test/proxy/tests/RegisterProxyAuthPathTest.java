package test.proxy.tests;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * REGISTER through the edge proxy: 407 challenge, digest, PATH echoed in the 200.
 */
public class RegisterProxyAuthPathTest extends TestCase {

    private static final int TIMEOUT = 15000;
    private static final String REALM = "test.mobius.local";
    private static final String USER = "alice";
    private static final String PASSWORD = "supersecret";

    private Registree registree;
    private EdgeProxy edgeProxy;
    private Registrar registrar;

    @Override
    public void setUp() throws Exception {
        int registreePort = NetworkPortAssigner.retrieveNextPort();
        int edgeProxyPort = NetworkPortAssigner.retrieveNextPort();
        int registrarPort = NetworkPortAssigner.retrieveNextPort();

        registree = new Registree(registreePort, edgeProxyPort, USER, PASSWORD);
        edgeProxy = new EdgeProxy(edgeProxyPort, registrarPort);
        registrar = new Registrar(registrarPort, REALM, PASSWORD);
    }

    public void testRegisterWithProxyAuthAndPath() throws Exception {
        registree.sendRegister();

        assertTrue("Registree should be challenged and then successfully registered",
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return registree.isChallengeSeen() && registree.isRegistered();
                    }
                }, TIMEOUT));
        assertTrue("Registrar should have sent the 407 challenge", registrar.isChallengeSent());
        assertTrue("Registrar should have accepted the digest credentials", registrar.isAuthenticationAccepted());
        assertEquals("Both REGISTERs should traverse the edge proxy", 2, edgeProxy.getRegistersForwarded());
        assertEquals("Registrar should have stored the Path of the edge proxy", 1,
                registrar.getRegisteredPaths().size());
        assertTrue("Stored Path should point at the edge proxy",
                registrar.getRegisteredPaths().get(0).contains(":" + edgeProxy.getPort()));
        assertEquals("200 OK should echo the Path header", 1, registree.getPathsInFinalResponse().size());
        assertTrue("Echoed Path should point at the edge proxy",
                registree.getPathsInFinalResponse().get(0).contains(":" + edgeProxy.getPort()));
    }

    @Override
    public void tearDown() throws Exception {
        registree.stop();
        edgeProxy.stop();
        registrar.stop();
    }
}
