package test.unit.gov.nist.javax.sip.stack.dialog.b2bua.forking;

import gov.nist.javax.sip.SipStackImpl;
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
    
    private static final int TIMEOUT = 15000;

    @Override 
    public void setUp() throws Exception {
        int alicePort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort = NetworkPortAssigner.retrieveNextPort();
        int b2bPort2 = NetworkPortAssigner.retrieveNextPort();        
        int proxyPort = NetworkPortAssigner.retrieveNextPort();        
        int bobPort = NetworkPortAssigner.retrieveNextPort();        
        int carolPort = NetworkPortAssigner.retrieveNextPort();
        this.alice = new Shootist(alicePort,b2bPort);
        ((SipStackImpl)this.alice.sipStack).setMaxForkTime(32);
        this.b2bua  = new BackToBackUserAgent(b2bPort,b2bPort2);        
        b2bua.addTargetPort(proxyPort);        
        new Proxy(proxyPort,2,new int[]{bobPort,carolPort});        
        this.bob = new Shootme(bobPort,true,100, 5000);
        this.carol = new Shootme(carolPort,true,1000, 5000);        
    }
    
    public void testAcceptsAll200OKFromAlice() {
        this.alice.byeDelay = 2000;
        this.alice.cancelDelay = -1;
        this.alice.sendInvite();
    }

    public void testCancelAllLegsFromAlice() {
        this.alice.cancelDelay = 2000;
        this.bob.waitForCancel = true;
        this.carol.waitForCancel = true;
        this.alice.sendInvite();        
    }

    public void testPrackAndUpdateFromAlice() {          
        this.alice.requireReliableProvisionalResponse = true;
        this.bob.sendReliableProvisionalResponse = true;
        this.carol.sendReliableProvisionalResponse = true;
        this.bob.ringingDelay = 200;
        this.carol.ringingDelay = 200;       
        this.bob.okDelay = 6000;
        this.carol.okDelay = 6000;       
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
        assertTrue(
                "Should see invite"
                + " and Should see BYE",
                AssertUntil.assertUntil(carol.getAssertion(), TIMEOUT));
        
        super.tearDown();
    }
}
