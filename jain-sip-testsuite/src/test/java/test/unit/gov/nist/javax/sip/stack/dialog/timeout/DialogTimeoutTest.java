/*
 * This source code has been contributed to the public domain by Mobicents
 *
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 *
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement.
 */
 package test.unit.gov.nist.javax.sip.stack.dialog.timeout;

import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import gov.nist.javax.sip.SipStackImpl;
import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.ScenarioHarness;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Testing if Dialog Timeout Event is correctly passed to the application layer on both sides if the 
 * ACK is not sent by the UAC and the autodialog flag is false only
 * 
 * @author jean deruelle
 *
 */
public class DialogTimeoutTest extends ScenarioHarness {

	private ProtocolObjects shootistProtocolObjs;

	private ProtocolObjects shootmeProtocolObjs;

	    
    protected Shootist shootist;
    
    protected ShootistNotImplementingSipListenerExt shootistNotImplementingSipListenerExt;

    private Shootme shootme;
    
    private ShootmeNotImplementingListener shootmeNotImplementingListener;

    private static final int TIMEOUT = 90000;

    // private static Logger logger = LogManager.getLogger(DialogTimeoutTest.class);
    
    static {
    	LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
    	Configuration configuration = logContext.getConfiguration();
    	if (configuration.getAppenders().isEmpty()) {
        	configuration.addAppender(console);
        }
    }

    public DialogTimeoutTest() {
        super("DialogTerminationOn500Test", true);

    }

    public void setUp() {
        doSetUp();

    }

    private void doSetUp() {
        try {    
            testPassed = true;        
            super.setUp();            
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("unexpected exception ");
        }
    }

    // check that the apps gets called for timeout event when no ack is received nor sent
    public void testDialogTimeoutSipListenerExt() throws InterruptedException {
        
            try {

                this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", false,false, true);
                ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);
                shootme = new Shootme(shootmeProtocolObjs);
                SipProvider shootmeProvider = shootme.createSipProvider();
                
            	this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", false,false, true);
                shootist = new Shootist(shootistProtocolObjs,shootme);
                SipProvider shootistProvider = shootist.createSipProvider();                
               
                shootist.init();
                providerTable.put(shootistProvider, shootist);

                shootme.init();
                providerTable.put(shootmeProvider, shootme);
                shootistProvider.addSipListener(shootist);
                shootmeProvider.addSipListener(shootme);

                getRiProtocolObjects().start();
                if (getTiProtocolObjects() != getRiProtocolObjects())
                    getTiProtocolObjects().start();
                
                this.shootist.sendInviteRequest();
                Thread.currentThread();
				Thread.sleep(TIMEOUT);
            } catch (Exception e) {
                fail("unexpected exception ",e);
            }

            assertTrue("Test Failed - Didnt receive Dialog Timeout Event: shootme state " + shootme.checkState() + " , shootist state:" + shootist.checkState(), 
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {                        
                        return shootme.checkState() && shootist.checkState();
                    };
                }, TIMEOUT)
            );

            // doTearDown(false);
            // doSetUp();
    }
    
    // check that the apps gets called for timeout event when no ack is received nor sent
    // and for terminated event after the BYE is sent
    public void testDialogTimeoutAndTerminatedSipListenerExt() throws InterruptedException {
        
        try {


            this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", false,false, true);
            ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);
            shootme = new Shootme(shootmeProtocolObjs);
            shootme.setReceiveBye(true);
            SipProvider shootmeProvider = shootme.createSipProvider();
            
            this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", false,false, true);
            shootist = new Shootist(shootistProtocolObjs,shootme);
            shootist.setSendByeOnDialogTimeout(true);
            SipProvider shootistProvider = shootist.createSipProvider();            
           
            shootist.init();
            providerTable.put(shootistProvider, shootist);

            shootme.init();
            providerTable.put(shootmeProvider, shootme);
            shootistProvider.addSipListener(shootist);
            shootmeProvider.addSipListener(shootme);

            getRiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
            
            this.shootist.sendInviteRequest();
            Thread.currentThread();
			Thread.sleep(TIMEOUT);
        } catch (Exception e) {
            fail("unexpected exception ", e);
        }

        
        assertTrue("Test Failed - Didnt receive Dialog Timeout Event: shootme state " + shootme.checkState() + " , shootist state:" + shootist.checkState(), 
            AssertUntil.assertUntil(new TestAssertion() {
                @Override
                public boolean assertCondition() {
                    return shootme.checkState() && shootist.checkState();
                };
            }, TIMEOUT)
        );        

        // doTearDown(false);
        // doSetUp();
    }
    
    // check that the caller application doesn't get called on timeout but called on dialog terminated event when it is not implementing the new listener
    public void testDialogTimeoutDialogDeletedNotImplementedSipListenerExt() throws InterruptedException {
        
        try {
            this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", false,false, true);
            ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);
            shootme = new Shootme(shootmeProtocolObjs);
            SipProvider shootmeProvider = shootme.createSipProvider();
            
            this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", true,false, true);
            shootistNotImplementingSipListenerExt = new ShootistNotImplementingSipListenerExt(shootistProtocolObjs, shootme);
            SipProvider shootistProvider = shootistNotImplementingSipListenerExt.createSipProvider();            
           
            shootistNotImplementingSipListenerExt.init();
            providerTable.put(shootistProvider, shootistNotImplementingSipListenerExt);

            shootme.init();
            providerTable.put(shootmeProvider, shootme);
            shootistProvider.addSipListener(shootistNotImplementingSipListenerExt);
            shootmeProvider.addSipListener(shootme);

            getRiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
            
            this.shootistNotImplementingSipListenerExt.sendInviteRequest();
            Thread.currentThread();
			Thread.sleep(TIMEOUT);
        } catch (Exception e) {
            fail("unexpected exception ",e);
        }

        
        assertTrue("Test Failed - Didnt receive Dialog Timeout Event: shootme state " + shootme.checkState() + " , shootist state:" + shootistNotImplementingSipListenerExt.checkState(),
            AssertUntil.assertUntil(new TestAssertion() {
                @Override
                public boolean assertCondition() {
                	return shootme.checkState() && shootistNotImplementingSipListenerExt.checkState();
                };
            }, TIMEOUT)
        );

        // doTearDown(false);
        // doSetUp();
    }
    
    // check that the apps don't get called on tiemout event if autodialog is true but get called on dialog terminated event
    public void testDialogTimeoutAutoDialog() throws InterruptedException {
        
        try {

            this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", true,false, true);
            ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);
            shootme = new Shootme(shootmeProtocolObjs);
            SipProvider shootmeProvider = shootme.createSipProvider();
            
            this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", true,false, true);
            shootist = new Shootist(shootistProtocolObjs, shootme);
            SipProvider shootistProvider = shootist.createSipProvider();            
           
            shootist.init();
            providerTable.put(shootistProvider, shootist);

            shootme.init();
            providerTable.put(shootmeProvider, shootme);
            shootistProvider.addSipListener(shootist);
            shootmeProvider.addSipListener(shootme);

            getRiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
            
            this.shootist.sendInviteRequest();
            Thread.currentThread();
			Thread.sleep(TIMEOUT);
        } catch (Exception e) {
            fail("unexpected exception ", e);
        }

        assertTrue("Test Failed - Didnt receive Dialog Timeout Event: shootme state " + shootme.checkState() + " , shootist state:" + shootist.checkState(), 
            AssertUntil.assertUntil(new TestAssertion() {
                @Override
                public boolean assertCondition() {
                    return shootme.checkState() && shootist.checkState();
                };
            }, TIMEOUT)
        );        

        // doTearDown(false);
        // doSetUp();
    }
    
    // test checking that when the B2BUA flag is set to true, the app doesn't get called on dialog timeout but get called for dialog terminated 
    public void testDialogTimeoutB2BUABothCalled() throws InterruptedException {
        
        try {

            this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", false,false, true);
            ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);
            shootmeNotImplementingListener = new ShootmeNotImplementingListener(shootmeProtocolObjs);
            shootmeNotImplementingListener.setStateIsOk(true);
            SipProvider shootmeProvider = shootmeNotImplementingListener.createSipProvider();

            this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", false,true, true);
            shootist = new Shootist(shootistProtocolObjs, shootmeNotImplementingListener);
            SipProvider shootistProvider = shootist.createSipProvider();            
            
            shootist.init();
            providerTable.put(shootistProvider, shootist);

            shootmeNotImplementingListener.init();
            providerTable.put(shootmeProvider, shootmeNotImplementingListener);
            shootistProvider.addSipListener(shootist);
            shootmeProvider.addSipListener(shootmeNotImplementingListener);

            getRiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
            
            this.shootist.sendInviteRequest();
            Thread.currentThread();
			Thread.sleep(TIMEOUT);
        } catch (Exception e) {
            fail("unexpected exception ", e);
        }
        
        assertTrue("Test Failed - Didnt receive Dialog Timeout Event: shootme state " + shootmeNotImplementingListener.checkState(), 
            AssertUntil.assertUntil(new TestAssertion() {
                @Override
                public boolean assertCondition() {
                    return shootmeNotImplementingListener.checkState();
                };
            }, TIMEOUT)
        );

        assertTrue("Test Failed - didn't received Dialog Terminated Event: shootist state:" + shootist.checkState(), 
            AssertUntil.assertUntil(new TestAssertion() {
                @Override
                public boolean assertCondition() {
                    return shootist.checkState();
                };
            }, TIMEOUT)
        );        

        // doTearDown(false);
        // doSetUp();
    }

    public void tearDown() {
        doTearDown(true);

    }

    private void doTearDown(boolean definetly) {
        try {
            Thread.sleep(3000);
            if (definetly) {
                TestCase.assertTrue("Test failed " + failureReason, testPassed);                
            }
            // this.shootist.checkState();
            // this.shootme.checkState();
            shootmeProtocolObjs.destroy();
            shootistProtocolObjs.destroy();
            Thread.sleep(1000);
            this.providerTable.clear();            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
