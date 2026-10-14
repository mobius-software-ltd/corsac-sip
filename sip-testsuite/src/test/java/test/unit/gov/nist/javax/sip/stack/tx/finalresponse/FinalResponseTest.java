package test.unit.gov.nist.javax.sip.stack.tx.finalresponse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.sip.ListeningPoint;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

public class FinalResponseTest {

    private static final int TIMEOUT = 10000;

    private int shootistPort;
    private int shootmePort;

    private Shootist shootist;
    private Shootme shootme;

    @Before
    public void setUp() throws Exception {
        shootistPort = NetworkPortAssigner.retrieveNextPort();
        shootmePort = NetworkPortAssigner.retrieveNextPort();
    }

    @After
    public void tearDown() throws Exception {
        if (shootist != null) {
            shootist.stop();
        }
        if (shootme != null) {
            shootme.stop();
        }
    }

    /** TPS-485: SIP RA settings, app dialog, TCP; INVITE, 100, 180, 480, stack ACKs, the 480 must reach the listener. */
    @Test(timeout = 60000)
    public void testRinging480SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, true);
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** Same as TPS-485 without an app dialog, as for an INVITE the RA sends on a transaction activity. */
    @Test(timeout = 60000)
    public void testRinging480SipRaNoAppDialogTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, false);
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** SIP RA settings, TCP; INVITE, 100, 480 with no 180, so no early dialog before the final response. */
    @Test(timeout = 60000)
    public void testDirect480SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, true);
        shootme.sendRinging = false;
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** SIP RA settings, TCP; the 480 carries another To tag than the 180, as when another hop generates it. */
    @Test(timeout = 60000)
    public void testRinging480OtherTagSipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, true);
        shootme.finalToTag = "other-hop";
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** SIP RA settings, TCP; tagged 180, then a 480 without a To tag. */
    @Test(timeout = 60000)
    public void testRingingUntagged480SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, true);
        shootme.finalToTag = null;
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** SIP RA settings, TCP; 180 without a To tag, so no early dialog, then a tagged 480. */
    @Test(timeout = 60000)
    public void testUntaggedRinging480SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, true);
        shootme.ringingToTag = null;
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** SIP RA settings, TCP; INVITE, 100, 180, 200: the 2xx terminates the transaction, the 200 must reach the listener first. */
    @Test(timeout = 60000)
    public void testRinging200SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, true);
        shootme.finalResponse = Response.OK;
        shootist.sendInvite();
        assertFinalResponse(Response.OK);
    }

    /** Stack defaults, TCP; INVITE, 100, 180, 480: the 480 always arrived here, but after the terminated event. */
    @Test(timeout = 60000)
    public void testRinging480Tcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.DEFAULT, false);
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** Automatic dialog support off, otherwise defaults, TCP; INVITE, 100, 180, 480, response before the terminated event. */
    @Test(timeout = 60000)
    public void testRinging480NoAutomaticDialogTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.NO_AUTOMATIC_DIALOG, false);
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** Stack defaults, TCP; INVITE, 100, 180, 200, the 200 must reach the listener before the terminated event. */
    @Test(timeout = 60000)
    public void testRinging200Tcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.DEFAULT, false);
        shootme.finalResponse = Response.OK;
        shootist.sendInvite();
        assertFinalResponse(Response.OK);
    }

    /** SIP RA settings, UDP; the 480 moves the transaction to Completed, Timer D terminates it later. */
    @Test(timeout = 60000)
    public void testRinging480SipRaUdp() throws Exception {
        start(ListeningPoint.UDP, StackSettings.SIP_RA, true);
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** Stack defaults, UDP; the 480 moves the transaction to Completed, Timer D terminates it later. */
    @Test(timeout = 60000)
    public void testRinging480Udp() throws Exception {
        start(ListeningPoint.UDP, StackSettings.DEFAULT, false);
        shootist.sendInvite();
        assertFinalResponse(Response.TEMPORARILY_UNAVAILABLE);
    }

    /** SIP RA settings, UDP; the 2xx terminates the INVITE transaction on UDP too, the 200 must reach the listener first. */
    @Test(timeout = 60000)
    public void testRinging200SipRaUdp() throws Exception {
        start(ListeningPoint.UDP, StackSettings.SIP_RA, true);
        shootme.finalResponse = Response.OK;
        shootist.sendInvite();
        assertFinalResponse(Response.OK);
    }

    /** Stack defaults, UDP; the 2xx terminates the INVITE transaction on UDP too, the 200 must reach the listener first. */
    @Test(timeout = 60000)
    public void testRinging200Udp() throws Exception {
        start(ListeningPoint.UDP, StackSettings.DEFAULT, false);
        shootme.finalResponse = Response.OK;
        shootist.sendInvite();
        assertFinalResponse(Response.OK);
    }

    /** SIP RA settings, TCP; MESSAGE, 200: a non-INVITE over TCP terminates on any final response, same as the INVITE 4xx. */
    @Test(timeout = 60000)
    public void testMessage200SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, false);
        shootme.finalResponse = Response.OK;
        shootist.sendRequest(Request.MESSAGE);
        assertFinalResponse(Response.OK);
    }

    /** SIP RA settings, TCP; MESSAGE, 404, the 404 must reach the listener. */
    @Test(timeout = 60000)
    public void testMessage404SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, false);
        shootme.finalResponse = Response.NOT_FOUND;
        shootist.sendRequest(Request.MESSAGE);
        assertFinalResponse(Response.NOT_FOUND);
    }

    /** SIP RA settings, TCP; OPTIONS, 200, the 200 must reach the listener. */
    @Test(timeout = 60000)
    public void testOptions200SipRaTcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.SIP_RA, false);
        shootme.finalResponse = Response.OK;
        shootist.sendRequest(Request.OPTIONS);
        assertFinalResponse(Response.OK);
    }

    /** Stack defaults, TCP; MESSAGE, 404: the 404 always arrived here, but after the terminated event. */
    @Test(timeout = 60000)
    public void testMessage404Tcp() throws Exception {
        start(ListeningPoint.TCP, StackSettings.DEFAULT, false);
        shootme.finalResponse = Response.NOT_FOUND;
        shootist.sendRequest(Request.MESSAGE);
        assertFinalResponse(Response.NOT_FOUND);
    }

    /** SIP RA settings, UDP; the non-INVITE moves to Completed, Timer K terminates it later. */
    @Test(timeout = 60000)
    public void testMessage404SipRaUdp() throws Exception {
        start(ListeningPoint.UDP, StackSettings.SIP_RA, false);
        shootme.finalResponse = Response.NOT_FOUND;
        shootist.sendRequest(Request.MESSAGE);
        assertFinalResponse(Response.NOT_FOUND);
    }

    private void start(String transport, StackSettings settings, boolean appDialog) {
        shootme = new Shootme(shootmePort, transport);
        shootist = new Shootist(shootistPort, shootmePort, transport, settings);
        shootist.appDialog = appDialog;
    }

    /** the final response reaches the listener, and before the transaction terminated event */
    private void assertFinalResponse(int status) throws Exception {
        boolean responseSeen = AssertUntil.assertUntil(shootist.getResponseAssertion(status), TIMEOUT);
        List<String> events = shootist.getEvents();
        assertTrue("listener must get the " + status + ", got " + events, responseSeen);
        int terminatedAt = events.indexOf(Shootist.TERMINATED);
        assertTrue("transaction terminated before the " + status + " reached the listener " + events,
                terminatedAt < 0 || terminatedAt > events.indexOf(String.valueOf(status)));
        assertFalse("unexpected transaction timeout on the UAC", shootist.isTimeoutSeen());
        assertFalse("IOException on the UAC", shootist.isIoExceptionSeen());
    }
}