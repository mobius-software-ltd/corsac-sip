package test.tck.msgflow.callflows.refer;

import javax.sip.SipListener;
import javax.sip.SipProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import test.tck.msgflow.callflows.ScenarioHarness;

/**
 *
 * Implements common setup and tearDown sequence for Refer tests
 *
 * @author M. Ranganathan
 * @author Ivelin Ivanov
 *
 */
public abstract class AbstractReferTestCase extends ScenarioHarness implements
        SipListener {


    protected Referee referee;

    protected Referrer referrer;

    private static Logger logger = LogManager.getLogger("test.tck");

    static {
    	LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
    	Configuration configuration = logContext.getConfiguration();
    	if (configuration.getAppenders().isEmpty()) {
        	configuration.addAppender(console);
        }
    }

    public AbstractReferTestCase() {
        super("refer", true);
    }

    public void setUp() throws Exception {
        try {
            super.setUp();

            logger.info("ReferTest: setup()");
            referee = new Referee(getTiProtocolObjects());
            SipProvider refereeProvider = referee.createProvider();
            providerTable.put(refereeProvider, referee);

            referrer = new Referrer(getRiProtocolObjects());
            SipProvider referrerProvider = referrer.createProvider();
            providerTable.put(referrerProvider, referrer);

            refereeProvider.addSipListener(this);
            referrerProvider.addSipListener(this);

            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
            getRiProtocolObjects().start();
        } catch (Exception ex) {
            logger.error("unexpected excecption ", ex);
            fail("unexpected exception");
        }
    }

    public void tearDown() throws Exception {
        try {
            Thread.sleep(1000);
            super.tearDown();
            Thread.sleep(1000);
            this.providerTable.clear();

            logger.info("referrer notify count=" + referrer.count);
            super.assertTrue(" Should have at least 3 NOTIFY", referrer.count >= 3);  // Should have 3 NOTIFYs

            logTestCompleted();
        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ", ex);
        }
        super.tearDown();
        Thread.sleep(1000);
    }
}