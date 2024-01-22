package test.unit.gov.nist.javax.sip.stack.dialog.b2bua.forking;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.dialog.b2bua.Shootist;
import test.unit.gov.nist.javax.sip.stack.dialog.b2bua.Shootme;

/**
 * This test is meant to test the B2BUA behavior when forking occurs.
 */
public class BackToBackUserAgentTest extends TestCase {
    
    private Shootist alice;
    private BackToBackUserAgent b2bua;
    private Shootme bob;
    private Shootme carol;
    private Proxy proxy;
    private static final int TIMEOUT = 8000;

    @Override 
    public void setUp() throws Exception {
        int alicePort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort2 = NetworkPortAssigner.retrieveNextPort();        
        int proxyPort = NetworkPortAssigner.retrieveNextPort();        
        int bobPort = NetworkPortAssigner.retrieveNextPort();        
        int carolPort = NetworkPortAssigner.retrieveNextPort();
        this.alice = new Shootist(alicePort,b2bPort);
        this.b2bua  = new BackToBackUserAgent(b2bPort,b2bPort2);        
        b2bua.addTargetPort(proxyPort);        
        // this.proxy = new Proxy(proxyPort,2,new int[]{bobPort,carolPort});
        this.proxy = new Proxy(proxyPort,1,new int[]{bobPort});
        this.bob = new Shootme(bobPort,true,100);
        this.carol = new Shootme(carolPort,true,100);
        
    }
    
    public void testInvite200OKBackToAlice() {
        this.alice.sendInvite();
    }
    
    @Override 
    public void tearDown() throws Exception {
        assertTrue(
                "Should see BYE response for ACKED Dialog"
                + " and InviteOK seen",
                AssertUntil.assertUntil(alice.getAssertion(), TIMEOUT));
        assertTrue(
                "Should see invite"
                + " and Should see BYE",
                AssertUntil.assertUntil(bob.getAssertion(), TIMEOUT));
        // assertTrue(
        //         "Should see invite"
        //         + " and Should see BYE",
        //         AssertUntil.assertUntil(carol.getAssertion(), TIMEOUT));
        
        super.tearDown();
    }
    

}
