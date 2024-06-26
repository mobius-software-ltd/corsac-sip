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
/**
 *
 */
package test.tck.msgflow.callflows.tls;

import java.util.EventObject;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import test.tck.msgflow.callflows.ScenarioHarness;

/**
/**
 * Shootist send INVITE
 * Shootme receives INVITE and sends 180, then 200 OK
 * Shootist receives OK and sends ACK. 
 * Shootist sends re-INVITE
 * Shootme receives re-INVITE and sends OK
 * Shootist receives OK and sends ACK. 
 * Shootme receives ACK and sends re-INVITE
 * Shootist receives re-INVITE and sends OK
 * Shootme receives OK and sends ACK
 * Shootist receives ACK and sends BYE
 * Shootme receives BYE and sends 200 OK
 * 
 * @author M. Ranganathan
 *
 */
public class TlsTest extends ScenarioHarness implements SipListener {


    protected Shootist shootist;

    private Shootme shootme;

    static {
    	LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
    	Configuration configuration = logContext.getConfiguration();
    	if (configuration.getAppenders().isEmpty()) {
        	configuration.addAppender(console);
        }
    }

    private SipListener getSipListener(EventObject sipEvent) {
        SipProvider source = (SipProvider) sipEvent.getSource();
        SipListener listener = (SipListener) providerTable.get(source);
        assertTrue(listener != null);
        return listener;
    }

    public TlsTest() {
        super("tlstest", true);
    }

    public void setUp() {

        try {
            // setup TLS properties
            System.setProperty( "javax.net.ssl.keyStore",  TlsTest.class.getResource("testkeys").getPath() );
            System.setProperty( "javax.net.ssl.trustStore", TlsTest.class.getResource("testkeys").getPath() );
            System.setProperty( "javax.net.ssl.keyStorePassword", "passphrase" );
            System.setProperty( "javax.net.ssl.keyStoreType", "jks" );

            this.transport = "tls";

            super.setUp();
        
            shootme = new Shootme(getTiProtocolObjects());
            SipProvider shootmeProvider = shootme.createSipProvider();
            providerTable.put(shootmeProvider, shootme);
            shootist = new Shootist(getRiProtocolObjects());
            SipProvider shootistProvider = shootist.createSipProvider();
            providerTable.put(shootistProvider, shootist);
            shootmeProvider.addSipListener(this);
            shootistProvider.addSipListener(this);
             
            getRiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("unexpected exception ");
        }
    }

    public void testTlsTestSendInvite() {
        this.shootist.sendInvite();
    }

    public void tearDown() {
        try {
            Thread.sleep(8000);
            this.shootist.checkState();
            this.shootme.checkState();
            getTiProtocolObjects().destroy();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getRiProtocolObjects().destroy();
            Thread.sleep(1000);
            this.providerTable.clear();

            System.clearProperty( "javax.net.ssl.keyStore" );
            System.clearProperty( "javax.net.ssl.trustStore" );
            System.clearProperty( "javax.net.ssl.keyStorePassword" );
            System.clearProperty( "javax.net.ssl.keyStoreType" );

            logTestCompleted();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void processRequest(RequestEvent requestEvent) {
        getSipListener(requestEvent).processRequest(requestEvent);

    }

    public void processResponse(ResponseEvent responseEvent) {
        getSipListener(responseEvent).processResponse(responseEvent);

    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        getSipListener(timeoutEvent).processTimeout(timeoutEvent);
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        fail("unexpected exception");

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        getSipListener(transactionTerminatedEvent)
                .processTransactionTerminated(transactionTerminatedEvent);

    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        getSipListener(dialogTerminatedEvent).processDialogTerminated(
                dialogTerminatedEvent);

    }

}
