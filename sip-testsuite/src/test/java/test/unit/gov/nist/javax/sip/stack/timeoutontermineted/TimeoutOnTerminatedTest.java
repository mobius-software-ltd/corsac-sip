/**
 *
 */
package test.unit.gov.nist.javax.sip.stack.timeoutontermineted;

import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;

import junit.framework.TestCase;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * @author <a href="mailto:baranowb@gmail.com"> Bartosz Baranowski </a>
 *
 */
public class TimeoutOnTerminatedTest extends TestCase {

    protected Shootist shootist;

    protected Shootme shootme;

    private static Logger logger = LogManager.getLogger("test.tck");

    protected static final Appender console = ConsoleAppender.newBuilder().setName("Console").build();
    
    private static final int TIMEOUT = 60000;    

    // private Appender appender;

    public TimeoutOnTerminatedTest() {

        super("timeoutontermineted");

    }

    @Override
    public void setUp() {

        try {
            super.setUp();
            int shootitsPort = NetworkPortAssigner.retrieveNextPort();
            int shootmePort = NetworkPortAssigner.retrieveNextPort();
            shootist = new Shootist(shootitsPort, shootmePort);
            SipProvider shootistProvider = shootist.createSipProvider();
            shootistProvider.addSipListener(shootist);

            shootme = new Shootme(shootmePort, 1000);

            SipProvider shootmeProvider = shootme.createProvider();
            shootmeProvider.addSipListener(shootme);

            logger.debug("setup completed");

        } catch (Exception ex) {
            fail("unexpected exception ");
        }
    }

    @Override
    public void tearDown() {
        try {
            AssertUntil.assertUntil(shootist.getAssertion(), TIMEOUT);
            AssertUntil.assertUntil(shootme.getAssertion(), TIMEOUT);

            this.shootist.checkState();

            this.shootme.checkState();

            this.shootist.stop();

            this.shootme.stop();

        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
    }

    public void testInvite() throws Exception {
        this.shootist.sendInvite();

    }

}
