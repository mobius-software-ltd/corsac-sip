/**
 *
 */
package test.tck.msgflow.callflows.forkedinvite;

import java.util.Hashtable;

import javax.sip.SipListener;
import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.ScenarioHarness;

/**
 * @author M. Ranganathan
 *
 */
public abstract class AbstractForkedInviteTestCase extends ScenarioHarness implements
        SipListener {


    protected Shootist shootist;

    private static Logger logger = LogManager.getLogger("test.tck");


    protected Shootme shootme;

    private Shootme shootme2;

    private static final int TIMEOUT = 8000;


    // private Appender appender;

    public AbstractForkedInviteTestCase() {

        super("forkedInviteTest", true);

        try {
            providerTable = new Hashtable<SipProvider,SipListener>();

        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
    }

    public void setUp() {

        try {
            super.setUp(false,1,3);
            int shootistPort = NetworkPortAssigner.retrieveNextPort();
            int proxyPort = NetworkPortAssigner.retrieveNextPort();
            int shootmePort = NetworkPortAssigner.retrieveNextPort();
            int shootme2Port = NetworkPortAssigner.retrieveNextPort();
            int[] targets = {shootmePort, shootme2Port};
            
            shootist = new Shootist(shootistPort, proxyPort, super.getTiProtocolObjects(0));
            SipProvider shootistProvider = shootist.createSipProvider();
            providerTable.put(shootistProvider, shootist);

            this.shootme = new Shootme(shootmePort, getTiProtocolObjects(1));
            SipProvider shootmeProvider = shootme.createProvider();
            providerTable.put(shootmeProvider, shootme);
            shootistProvider.addSipListener(this);
            shootmeProvider.addSipListener(this);



            this.shootme2 = new Shootme(shootme2Port, getTiProtocolObjects(2));
            shootmeProvider = shootme2.createProvider();
            providerTable.put(shootmeProvider, shootme2);
            shootistProvider.addSipListener(this);
            shootmeProvider.addSipListener(this);

            Proxy proxy = new Proxy(proxyPort, getRiProtocolObjects(0), targets);
            SipProvider provider = proxy.createSipProvider();
            provider.setAutomaticDialogSupportEnabled(false);
            providerTable.put(provider, proxy);
            provider.addSipListener(this);

            super.start();
        } catch (Exception ex) {
            System.out.println(ex.toString());
            fail("unexpected exception ");
        }
    }




    public void tearDown() {
        try {
            assertTrue(
                    "Should see two distinct dialogs and Should see the original (default) dialog in the forked set",
                    AssertUntil.assertUntil(shootist.getAssertion(), TIMEOUT));
            assertTrue(
                    "Should see invite and Should see either an ACK or a BYE, or both",
                    AssertUntil.assertUntil(shootme.getAssertion(), TIMEOUT));
            assertTrue(
                    "Should see invite and Should see either an ACK or a BYE, or both",
                    AssertUntil.assertUntil(shootme2.getAssertion(), TIMEOUT));
            super.tearDown();
            Thread.sleep(2000);
            this.providerTable.clear();
            super.logTestCompleted();
            Thread.sleep(2000);
        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
    }
}