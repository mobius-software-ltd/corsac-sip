/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), and others.
* This software is has been contributed to the public domain.
* As a result, a formal license is not needed to use the software.
*
* This software is provided "AS IS."
* NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
*
*/
package test.tck.msgflow.callflows.router;

import javax.sip.SipListener;
import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NonSipUriRouter;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.ScenarioHarness;

/**
 *
 * Implements common setup and tearDown sequence for Router test
 *
 * @author M. Ranganathan
 *
 */
public abstract class AbstractRouterTestCase extends ScenarioHarness implements
        SipListener {


    protected Shootist shootist;

    protected Shootme shootme;

    private static Logger logger = LogManager.getLogger("test.tck");
    
    private static final int TIMEOUT = 2000;

    static {
    	LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
    	Configuration configuration = logContext.getConfiguration();
    	if (configuration.getAppenders().isEmpty()) {
        	configuration.addAppender(console);
        }
    }

    public AbstractRouterTestCase() {
        super("routeteluri", true);
    }

    public void setUp() throws Exception {
        try {
            super.setUp();

            shootme = new Shootme(getRiProtocolObjects());
            SipProvider shootmeProvider = shootme.createProvider();
            providerTable.put(shootmeProvider, shootme);
            
            logger.info("RouterTest: setup()");
            ProtocolObjects protocolObjects = new ProtocolObjects("shootist","gov.nist", transport, Shootme.myPort, true,false, false); 
            shootist = new Shootist(protocolObjects);
            SipProvider shootistProvider = shootist.createProvider();
            providerTable.put(shootistProvider, shootist);

            shootistProvider.addSipListener(this);
            shootmeProvider.addSipListener(this);

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
            assertTrue(AssertUntil.assertUntil(shootist.getAssertion(), TIMEOUT));
            assertTrue(AssertUntil.assertUntil(shootme.getAssertion(), TIMEOUT));
            assertTrue("Router was not consulted", NonSipUriRouter.routerWasConsulted);
            NonSipUriRouter.routerWasConsulted = false;
            this.providerTable.clear();

            logTestCompleted();
        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
        super.tearDown();
        Thread.sleep(2000);
    }
}
