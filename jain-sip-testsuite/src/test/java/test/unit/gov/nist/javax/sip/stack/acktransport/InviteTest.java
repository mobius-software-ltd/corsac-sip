/**
 * 
 */
package test.unit.gov.nist.javax.sip.stack.acktransport;

import java.util.HashSet;

import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;

import junit.framework.TestCase;

import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * @author M. Ranganathan
 * 
 */
public class InviteTest extends TestCase {

    private static Logger logger = LogManager.getLogger("test.tck");

    protected static final Appender console = ConsoleAppender.newBuilder().setName("Console").build();    
   

    protected HashSet<Shootme> shootme = new HashSet<Shootme>();

  

    private Proxy proxy;

    // private Appender appender;

    public InviteTest() {

        super("");

    }

    public void setUp() {

        try {
            super.setUp();
            

        } catch (Exception ex) {
            fail("unexpected exception ");
        }
    }

    public void tearDown() {
        try {
            
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
    }

    public void testSendInvite() throws Exception {
        try {
            int shootmePort = NetworkPortAssigner.retrieveNextPort();
            int proxyPort = NetworkPortAssigner.retrieveNextPort();
            int shootistPort = NetworkPortAssigner.retrieveNextPort();
            
           
            Shootme shootmeUa = new Shootme(shootmePort, true, 4000, 4000);
            SipProvider shootmeProvider = shootmeUa.createProvider();
            shootmeProvider.addSipListener(shootmeUa);

        
            this.proxy = new Proxy(proxyPort);
            proxy.setTargetPort(shootmePort);
            SipProvider provider = proxy.createSipProvider("tcp");
            provider.addSipListener(proxy);
            provider = proxy.createSipProvider("udp");
            provider.addSipListener(proxy);
            
            
            Shootist shootist = new Shootist(shootistPort, proxyPort);
            SipProvider shootistProvider = shootist.createSipProvider();
            shootistProvider.addSipListener(shootist);
          //  shootistProvider = shootist.createSipProvider("udp");
          //  shootistProvider.addSipListener(shootist);

            shootist.sendInvite(1);

            Thread.sleep(30000);
            
            shootmeUa.checkState();
            shootist.checkState();
            shootist.stop();
            shootmeUa.stop();
            proxy.stop();
        } finally {
           
        }
    }

}
