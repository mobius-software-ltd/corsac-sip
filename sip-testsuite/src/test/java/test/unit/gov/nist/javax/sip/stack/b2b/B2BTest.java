package test.unit.gov.nist.javax.sip.stack.b2b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import gov.nist.javax.sip.DialogTimeoutEvent;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.b2b.Records.MessageRecord;

/**
 * B2BUA call flows: shootist (UAC) - B2BUA - shootme (UAS),
 */
public class B2BTest {

    private static final int TIMEOUT = 10000;
    /** 64*T1 = 32 s for the ACK, then the BYEs */
    private static final int ACK_TIMEOUT = 45000;
    /** EARLY_DIALOG_TIMEOUT_SECONDS on the B2B */
    private static final int EARLY_DIALOG_TIMEOUT = 30;
    /** past the 30 s early dialog timeout, then the 408 */
    private static final int EARLY_STATE_TIMEOUT = 45000;
    /** dialogs linger 8 s, UDP non-INVITE server transactions wait Timer J (32 s) */
    private static final int CLEANUP = 40000;

    private int shootistPort;
    private int b2bPort;
    private int b2bPeerPort;
    private int shootmePort;
    private int shootme2Port;

    private Shootist shootist;
    private BackToBackUserAgent b2b;
    private Shootme shootme;
    private Shootme shootme2;

    @Before
    public void setUp() throws Exception {
        shootistPort = NetworkPortAssigner.retrieveNextPort();
        b2bPort = NetworkPortAssigner.retrieveNextPort();
        b2bPeerPort = NetworkPortAssigner.retrieveNextPort();
        shootmePort = NetworkPortAssigner.retrieveNextPort();
        shootme2Port = NetworkPortAssigner.retrieveNextPort();
    }

    @After
    public void tearDown() throws Exception {
        if (shootist != null) {
            shootist.stop();
        }
        if (b2b != null) {
            b2b.stop();
        }
        if (shootme != null) {
            shootme.stop();
        }
        if (shootme2 != null) {
            shootme2.stop();
        }
    }

    /** Flow 1: regular call, BYE from the UAS. */
    @Test(timeout = 90000)
    public void testRegularFlowWithByeFromUAS() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        regularFlowWithByeFromUAS();
    }

    /** Flow 2: UPDATE from the UAC in the early dialog, then 200 INVITE, BYE from the UAC. */
    @Test(timeout = 90000)
    public void testUpdateInEarlyDialogWithByeFromUAC() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        // 2xx well after the early UPDATE so they cannot race
        shootme.okDelay = 1500;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);
        shootist.sendUpdate = true;
        shootist.byeDelay = 1000;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("legs must be linked", AssertUntil.assertUntil(b2b.getLinkedAssertion(), TIMEOUT));
        assertTrue("UAC must get 200 to its BYE", AssertUntil.assertUntil(shootist.getByeOkAssertion(), TIMEOUT));
        assertTrue("BYE must reach the UAS", AssertUntil.assertUntil(shootme.getByeAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/UPDATE",
                "200/INVITE", "200/BYE");
        assertMessages("UAC requests", shootist.getReceivedRequests());
        assertMessages("UAC sent", shootist.getSentRequests(), "INVITE", "UPDATE", "ACK", "BYE");
        assertEquals("BYE after INVITE and UPDATE", 3, shootist.getSentRequests().get(3).cseq);
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "UPDATE", "ACK", "BYE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/UPDATE",
                "200/INVITE", "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "UPDATE", "ACK", "BYE");
        assertMessages("UAS responses", shootme.getReceivedResponses());

        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        assertUnreliable183(shootist.getResponses(Response.SESSION_PROGRESS, Request.INVITE).get(0));
        MessageRecord update = Records.requests(shootme.getReceivedRequests(), Request.UPDATE).get(0);
        assertEquals("UPDATE on the UAS early dialog", uasTag(), update.toTag);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 3: reliable 183 from the UAS, PRACKed by the B2B, relayed to the UAC without RSeq. */
    @Test(timeout = 90000)
    public void testReliableProvisionalWithPrack() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.sendReliableProvisional = true;
        shootme.byeDelay = 1000;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("legs must be linked", AssertUntil.assertUntil(b2b.getLinkedAssertion(), TIMEOUT));
        assertTrue("BYE from the UAS must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), TIMEOUT));
        assertTrue("UAS must get 200 to its BYE", AssertUntil.assertUntil(shootme.getByeOkAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "ACK", "BYE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/PRACK",
                "200/INVITE", "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "PRACK", "ACK");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "200/BYE");

        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        MessageRecord reliable = Records.responses(b2b.getReceivedResponses(), Response.SESSION_PROGRESS,
                Request.INVITE).get(0);
        assertTrue("183 from the UAS must carry RSeq", reliable.rseq);
        assertTrue(reliable.require100rel);
        assertUnreliable183(shootist.getResponses(Response.SESSION_PROGRESS, Request.INVITE).get(0));
        MessageRecord prack = Records.requests(shootme.getReceivedRequests(), Request.PRACK).get(0);
        assertEquals("PRACK on the UAS early dialog", uasTag(), prack.toTag);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 4: flow 1 with automatic dialog support off on the B2B. */
    @Test(timeout = 90000)
    public void testRegularFlowWithByeFromUASWithoutAutomaticDialogs() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, false, null, shootmePort);
        regularFlowWithByeFromUAS();
    }

    /** Flow 5: re-INVITE from the UAS, BYE from the UAS. */
    @Test(timeout = 90000)
    public void testReInviteFromUAS() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.reInviteDelay = 500;
        shootme.byeDelay = 1000;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("re-INVITE must be answered 200",
                AssertUntil.assertUntil(shootme.getResponseAssertion(Response.OK, Request.INVITE), TIMEOUT));
        assertTrue("legs stay linked after the re-INVITE", b2b.isLinked());
        assertTrue("BYE from the UAS must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), TIMEOUT));
        assertTrue("UAS must get 200 to its BYE", AssertUntil.assertUntil(shootme.getByeOkAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "INVITE", "ACK", "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "ACK", "INVITE", "ACK", "BYE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE",
                "100/INVITE", "200/INVITE", "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "ACK");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "100/INVITE", "200/INVITE", "200/BYE");

        MessageRecord invite = shootist.getFirstSentRequest();
        MessageRecord reInvite = Records.requests(shootist.getReceivedRequests(), Request.INVITE).get(0);
        assertEquals(invite.callId, reInvite.callId);
        assertEquals("re-INVITE To tag is the UAC From tag", invite.fromTag, reInvite.toTag);
        assertEquals("re-INVITE From tag is the B2B tag on leg A",
                shootist.getResponses(Response.OK, Request.INVITE).get(0).toTag, reInvite.fromTag);
        MessageRecord sentReInvite = Records.requests(shootme.getSentRequests(), Request.INVITE).get(0);
        assertEquals(sentReInvite.cseq,
                Records.responses(shootme.getReceivedResponses(), Response.OK, Request.INVITE).get(0).cseq);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 6: re-INVITE from the UAC, BYE from the UAS, over UDP. */
    @Test(timeout = 120000)
    public void testReInviteFromUACOverUDP() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.UDP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.UDP);
        shootme.byeDelay = 2000;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.UDP, null);
        shootist.reInviteDelay = 500;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("re-INVITE must be answered 200",
                AssertUntil.assertUntil(shootist.getResponseAssertion(Response.OK, Request.INVITE), TIMEOUT));
        assertTrue("BYE from the UAS must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), TIMEOUT));
        assertTrue("UAS must get 200 to its BYE", AssertUntil.assertUntil(shootme.getByeOkAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE",
                "100/INVITE", "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "ACK", "INVITE", "ACK", "BYE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE",
                "100/INVITE", "200/INVITE", "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "ACK", "INVITE", "ACK");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "200/BYE");

        MessageRecord reInvite = Records.requests(shootme.getReceivedRequests(), Request.INVITE).get(1);
        assertEquals("re-INVITE on the UAS dialog", uasTag(), reInvite.toTag);
        List<MessageRecord> oks = shootist.getResponses(Response.OK, Request.INVITE);
        assertEquals("200 to the re-INVITE", 2, oks.get(oks.size() - 1).cseq);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 7: re-INVITE from the UAS rejected by the UAC with 488, BYE from the UAS. */
    @Test(timeout = 90000)
    public void testReInviteFromUASRejected488() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.reInviteDelay = 500;
        shootme.byeDelay = 1000;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);
        shootist.reInviteResponse = Response.NOT_ACCEPTABLE_HERE;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("re-INVITE must be rejected 488", AssertUntil.assertUntil(
                shootme.getResponseAssertion(Response.NOT_ACCEPTABLE_HERE, Request.INVITE), TIMEOUT));
        assertTrue("legs stay linked after the 488", b2b.isLinked());
        assertTrue("BYE from the UAS must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), TIMEOUT));
        assertTrue("UAS must get 200 to its BYE", AssertUntil.assertUntil(shootme.getByeOkAssertion(), TIMEOUT));
        assertNothingLeft();

        // the ACKs for the 488 stay in the INVITE transactions
        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "INVITE", "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "ACK", "INVITE", "BYE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE",
                "100/INVITE", "488/INVITE", "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "ACK");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "100/INVITE", "488/INVITE", "200/BYE");

        MessageRecord invite = shootist.getFirstSentRequest();
        MessageRecord reInvite = Records.requests(shootist.getReceivedRequests(), Request.INVITE).get(0);
        assertEquals(invite.callId, reInvite.callId);
        assertEquals(invite.fromTag, reInvite.toTag);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 8: SUBSCRIBE, NOTIFY, un-SUBSCRIBE, NOTIFY (terminated). */
    @Test(timeout = 90000)
    public void testSubscribeNotifyUnsubscribe() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);
        shootist.unsubscribeDelay = 500;

        shootist.sendSubscribe();
        assertTrue("terminating NOTIFY must reach the UAC",
                AssertUntil.assertUntil(shootist.getNotifyTerminatedAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "200/SUBSCRIBE", "200/SUBSCRIBE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "NOTIFY", "NOTIFY");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "SUBSCRIBE", "NOTIFY", "SUBSCRIBE", "NOTIFY");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "200/SUBSCRIBE", "200/NOTIFY", "200/SUBSCRIBE",
                "200/NOTIFY");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "SUBSCRIBE", "SUBSCRIBE");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "200/NOTIFY", "200/NOTIFY");
        assertFalse(shootist.isNotifyBeforeSubscribeOk());

        MessageRecord subscribe = shootme.getFirstReceivedRequest();
        assertLegB(shootist.getFirstSentRequest(), subscribe, shootmePort);
        assertEquals(Shootist.EVENT, subscribe.event);
        assertEquals(Integer.valueOf(Shootist.SUBSCRIBE_EXPIRES), subscribe.expires);
        assertNotNull("200 to SUBSCRIBE must carry Expires",
                shootist.getResponses(Response.OK, Request.SUBSCRIBE).get(0).expires);
        MessageRecord unsubscribe = shootme.getReceivedRequests().get(1);
        assertEquals(Integer.valueOf(0), unsubscribe.expires);
        assertEquals("un-SUBSCRIBE on the UAS dialog",
                Records.responses(b2b.getReceivedResponses(), Response.OK, Request.SUBSCRIBE).get(0).toTag,
                unsubscribe.toTag);
        List<MessageRecord> notifies = shootist.getReceivedRequests();
        assertTrue(SubscriptionStateHeader.ACTIVE.equalsIgnoreCase(notifies.get(0).subscriptionState));
        assertTrue(SubscriptionStateHeader.TERMINATED.equalsIgnoreCase(notifies.get(1).subscriptionState));
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 9: SUBSCRIBE, NOTIFY before the 200 SUBSCRIBE, NOTIFY (terminated) from the UAS. */
    @Test(timeout = 90000)
    public void testSubscribeWithEarlyNotify() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.earlyNotify = true;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendSubscribe();
        assertTrue("terminating NOTIFY must reach the UAC",
                AssertUntil.assertUntil(shootist.getNotifyTerminatedAssertion(), TIMEOUT));
        assertNothingLeft();

        assertTrue("NOTIFY must reach the UAC before the 200 to the SUBSCRIBE", shootist.isNotifyBeforeSubscribeOk());
        assertMessages("UAC responses", shootist.getReceivedResponses(), "200/SUBSCRIBE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "NOTIFY", "NOTIFY");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "SUBSCRIBE", "NOTIFY", "NOTIFY");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "200/SUBSCRIBE", "200/NOTIFY", "200/NOTIFY");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "SUBSCRIBE");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "200/NOTIFY", "200/NOTIFY");

        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        MessageRecord subscribeOk = shootist.getResponses(Response.OK, Request.SUBSCRIBE).get(0);
        assertNotNull(subscribeOk.expires);
        assertEquals("200 to SUBSCRIBE and early NOTIFY share the B2B tag",
                shootist.getReceivedRequests().get(0).fromTag, subscribeOk.toTag);
        assertTrue(SubscriptionStateHeader.TERMINATED
                .equalsIgnoreCase(shootist.getReceivedRequests().get(1).subscriptionState));
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 10: SUBSCRIBE rejected with 489. */
    @Test(timeout = 90000)
    public void testSubscribeRejected489() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.subscribeResponse = Response.BAD_EVENT;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendSubscribe();
        assertTrue("UAC must get the 489", AssertUntil
                .assertUntil(shootist.getResponseAssertion(Response.BAD_EVENT, Request.SUBSCRIBE), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "489/SUBSCRIBE");
        assertMessages("UAC requests", shootist.getReceivedRequests());
        assertMessages("B2B requests", b2b.getReceivedRequests(), "SUBSCRIBE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "489/SUBSCRIBE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "SUBSCRIBE");
        assertMessages("UAS responses", shootme.getReceivedResponses());
        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 11: INVITE rejected with 486. */
    @Test(timeout = 90000)
    public void testInviteRejected486() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.sendSessionProgress = false;
        shootme.inviteResponse = Response.BUSY_HERE;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendInvite();
        assertTrue("UAC must get the 486",
                AssertUntil.assertUntil(shootist.getResponseAssertion(Response.BUSY_HERE, Request.INVITE), TIMEOUT));
        assertNothingLeft();

        // the ACKs for the 486 stay in the INVITE transactions
        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "486/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests());
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "486/INVITE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE");
        assertMessages("UAS responses", shootme.getReceivedResponses());
        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 12: CANCEL from the UAC after 183. */
    @Test(timeout = 90000)
    public void testCancelFromUAC() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.inviteResponse = 0;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);
        shootist.cancelOnProvisional = true;

        shootist.sendInvite();
        assertTrue("UAC must get the 487", AssertUntil
                .assertUntil(shootist.getResponseAssertion(Response.REQUEST_TERMINATED, Request.INVITE), TIMEOUT));
        assertTrue("CANCEL must reach the UAS",
                AssertUntil.assertUntil(shootme.getReceivedRequestsAssertion(Request.CANCEL, 1), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/CANCEL",
                "487/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests());
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "CANCEL");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/CANCEL",
                "487/INVITE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "CANCEL");
        assertMessages("UAS responses", shootme.getReceivedResponses());

        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        assertEquals("CANCEL on the leg B INVITE branch", shootme.getFirstReceivedRequest().viaBranch,
                shootme.getReceivedRequests().get(1).viaBranch);
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    /** Flow 13: the UAC never ACKs the 200; on the ACK timeout the B2B sends BYE on both legs. */
    @Test(timeout = 120000)
    public void testAckTimeoutFromUAC() throws Exception {
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, null, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);
        shootist.dropAck = true;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("BYE from the B2B must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), ACK_TIMEOUT));
        assertTrue("BYE from the B2B must reach the UAS", AssertUntil.assertUntil(shootme.getByeAssertion(), TIMEOUT));
        assertNothingLeft();

        assertTrue("B2B must see the ACK timeout " + b2b.getDialogTimeoutReasons(),
                b2b.getDialogTimeoutReasons().contains(DialogTimeoutEvent.Reason.AckNotReceived));
        assertTrue("200 to the INVITE must be retransmitted while the ACK is missing",
                shootist.getResponses(Response.OK, Request.INVITE).size() > 1);
        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE");
        // both BYEs go out together
        assertMessagesAnyOrder("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE",
                "200/BYE", "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "ACK", "BYE");
        assertMessages("UAS responses", shootme.getReceivedResponses());

        assertEquals("BYE on leg A", shootist.getFirstSentRequest().fromTag,
                shootist.getReceivedRequests().get(0).toTag);
        assertEquals("BYE on leg B", uasTag(), shootme.getReceivedRequests().get(2).toTag);
        assertFalse(b2b.isLinked());
        assertTrue(shootist.getDialogTimeoutReasons().size() <= 1);
        assertNoIo();
        assertNoTimeouts();
    }

    /** Flow 14: the UAS never answers after 183; on the early state timeout the B2B sends 408 to the UAC, CANCEL to the UAS. */
    @Test(timeout = 120000)
    public void testEarlyStateTimeout() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.EARLY_DIALOG_TIMEOUT_SECONDS",
                Integer.toString(EARLY_DIALOG_TIMEOUT));
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, properties, shootmePort);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.inviteResponse = 0;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendInvite();
        assertTrue("UAC must get the 408", AssertUntil.assertUntil(
                shootist.getResponseAssertion(Response.REQUEST_TIMEOUT, Request.INVITE), EARLY_STATE_TIMEOUT));
        assertTrue("CANCEL must reach the UAS",
                AssertUntil.assertUntil(shootme.getReceivedRequestsAssertion(Request.CANCEL, 1), TIMEOUT));
        assertNothingLeft();

        assertTrue("B2B must see the early state timeout " + b2b.getDialogTimeoutReasons(),
                b2b.getDialogTimeoutReasons().contains(DialogTimeoutEvent.Reason.EarlyStateTimeout));
        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "408/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests());
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/CANCEL",
                "487/INVITE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "CANCEL");
        assertMessages("UAS responses", shootme.getReceivedResponses());
        assertEquals("CANCEL on the leg B INVITE branch", shootme.getFirstReceivedRequest().viaBranch,
                shootme.getReceivedRequests().get(1).viaBranch);
        assertFalse(b2b.isLinked());
        assertTrue(shootist.getDialogTimeoutReasons().size() <= 1);
        assertNoIo();
        assertNoTimeouts();
    }

    /**
     * Flow 15: the B2B forks to two UASes, both send 183, the call goes on with the first;
     * the late 200 of the second is ACKed and released with BYE.
     */
    @Test(timeout = 120000)
    public void testForkedCallWithLate200FromSecondUAS() throws Exception {
        // corsac keeps the last response for sendForkedResponse only with a fork time set
        Properties b2bProperties = new Properties();
        b2bProperties.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "32");
        b2b = new BackToBackUserAgent(b2bPort, b2bPeerPort, ListeningPoint.TCP, true, b2bProperties, shootmePort,
                shootme2Port);
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.okDelay = 1000;
        shootme.byeDelay = 3000;
        shootme2 = new Shootme(shootme2Port, ListeningPoint.TCP);
        shootme2.progressDelay = 300;
        shootme2.okDelay = 2500;
        // the UAC keeps the early dialog of the losing fork until its early state timer fires
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "32");
        properties.setProperty("gov.nist.javax.sip.EARLY_DIALOG_TIMEOUT_SECONDS",
                Integer.toString(EARLY_DIALOG_TIMEOUT));
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, properties);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("late 200 from the second UAS must be released",
                AssertUntil.assertUntil(b2b.getForkedDialogsTerminatedAssertion(1), TIMEOUT));
        assertTrue("BYE must reach the second UAS", AssertUntil.assertUntil(shootme2.getByeAssertion(), TIMEOUT));
        assertTrue("releasing the losing fork must not touch the call", b2b.isLinked());
        assertTrue("BYE from the first UAS must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), TIMEOUT));
        assertTrue("first UAS must get 200 to its BYE", AssertUntil.assertUntil(shootme.getByeOkAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "183/INVITE",
                "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "ACK", "BYE");
        // the forks share the early dialog until the To tags differ, so the second 100 comes flagged as a retransmission
        assertEquals("one 100 per fork", 2,
                Records.responses(b2b.getReceivedResponses(), Response.TRYING, Request.INVITE).size());
        // the forks answer in parallel
        assertMessagesAnyOrder("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "183/INVITE",
                "200/INVITE", "200/INVITE", "200/BYE", "200/BYE");
        assertMessages("UAS1 requests", shootme.getReceivedRequests(), "INVITE", "ACK");
        assertMessages("UAS1 responses", shootme.getReceivedResponses(), "200/BYE");
        assertMessages("UAS2 requests", shootme2.getReceivedRequests(), "INVITE", "ACK", "BYE");
        assertMessages("UAS2 responses", shootme2.getReceivedResponses());

        MessageRecord fork1 = shootme.getFirstReceivedRequest();
        MessageRecord fork2 = shootme2.getFirstReceivedRequest();
        assertLegB(shootist.getFirstSentRequest(), fork1, shootmePort);
        assertLegB(shootist.getFirstSentRequest(), fork2, shootme2Port);
        assertEquals("one leg B call", fork1.callId, fork2.callId);
        assertEquals(fork1.fromTag, fork2.fromTag);
        assertFalse("one transaction per fork", fork1.viaBranch.equals(fork2.viaBranch));
        List<MessageRecord> provisionals = shootist.getResponses(Response.SESSION_PROGRESS, Request.INVITE);
        assertFalse("one early dialog per fork", provisionals.get(0).toTag.equals(provisionals.get(1).toTag));
        assertEquals("200 on the first fork's early dialog", provisionals.get(0).toTag,
                shootist.getResponses(Response.OK, Request.INVITE).get(0).toTag);
        assertEquals(1, b2b.getForkedDialogsTerminated());
        assertTrue("UAC must see the early state timeout of the losing fork " + shootist.getDialogTimeoutReasons(),
                shootist.getDialogTimeoutReasons().contains(DialogTimeoutEvent.Reason.EarlyStateTimeout));
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
    }

    /** flows 1 and 4 */
    private void regularFlowWithByeFromUAS() throws Exception {
        shootme = new Shootme(shootmePort, ListeningPoint.TCP);
        shootme.byeDelay = 1000;
        shootist = new Shootist(shootistPort, b2bPort, ListeningPoint.TCP, null);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("legs must be linked", AssertUntil.assertUntil(b2b.getLinkedAssertion(), TIMEOUT));
        assertTrue("BYE from the UAS must reach the UAC",
                AssertUntil.assertUntil(shootist.getByeReceivedAssertion(), TIMEOUT));
        assertTrue("UAS must get 200 to its BYE", AssertUntil.assertUntil(shootme.getByeOkAssertion(), TIMEOUT));
        assertNothingLeft();

        assertMessages("UAC responses", shootist.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE");
        assertMessages("UAC requests", shootist.getReceivedRequests(), "BYE");
        assertMessages("B2B requests", b2b.getReceivedRequests(), "INVITE", "ACK", "BYE");
        assertMessages("B2B responses", b2b.getReceivedResponses(), "100/INVITE", "183/INVITE", "200/INVITE",
                "200/BYE");
        assertMessages("UAS requests", shootme.getReceivedRequests(), "INVITE", "ACK");
        assertMessages("UAS responses", shootme.getReceivedResponses(), "200/BYE");

        assertLegB(shootist.getFirstSentRequest(), shootme.getFirstReceivedRequest(), shootmePort);
        assertUnreliable183(shootist.getResponses(Response.SESSION_PROGRESS, Request.INVITE).get(0));
        assertFalse(b2b.isLinked());
        assertNoIo();
        assertNoTimeouts();
        assertNoDialogTimeouts();
    }

    private static void assertMessages(String what, List<MessageRecord> records, String... expected) {
        assertEquals(what, Arrays.asList(expected), Records.names(records));
    }

    private static void assertMessagesAnyOrder(String what, List<MessageRecord> records, String... expected) {
        List<String> expectedNames = new ArrayList<String>(Arrays.asList(expected));
        List<String> names = Records.names(records);
        Collections.sort(expectedNames);
        Collections.sort(names);
        assertEquals(what, expectedNames, names);
    }

    /** leg B request: own Call-ID, From tag and branch, sent by the B2B leg B, no routing headers */
    private void assertLegB(MessageRecord legA, MessageRecord legB, int targetPort) {
        assertNotNull(legA);
        assertNotNull(legB);
        assertEquals(legA.method, legB.method);
        assertFalse("leg B has its own Call-ID", legA.callId.equals(legB.callId));
        assertFalse("leg B has its own From tag", legA.fromTag.equals(legB.fromTag));
        assertNull(legB.toTag);
        assertFalse("leg B has its own branch", legA.viaBranch.equals(legB.viaBranch));
        assertTrue("Request-URI " + legB.requestUri, legB.requestUri.startsWith("sip:callee@127.0.0.1:" + targetPort));
        assertEquals("127.0.0.1:" + b2bPeerPort, legB.viaSentBy);
        assertTrue("Contact " + legB.contact, legB.contact.startsWith("sip:b2b@127.0.0.1:" + b2bPeerPort));
        assertTrue(legB.routes.isEmpty());
        assertTrue(legB.recordRoutes.isEmpty());
    }

    private static void assertUnreliable183(MessageRecord progress) {
        assertFalse("183 on leg A must not carry RSeq", progress.rseq);
        assertFalse("183 on leg A must not require 100rel", progress.require100rel);
    }

    /** To tag of the (first) UAS, as the B2B got it */
    private String uasTag() {
        return Records.responses(b2b.getReceivedResponses(), Response.SESSION_PROGRESS, Request.INVITE).get(0).toTag;
    }

    private void assertNoIo() {
        assertFalse("IOException on UAC", shootist.isIoExceptionSeen());
        assertFalse("IOException on B2B", b2b.isIoExceptionSeen());
        assertFalse("IOException on UAS", shootme.isIoExceptionSeen());
        if (shootme2 != null) {
            assertFalse("IOException on UAS2", shootme2.isIoExceptionSeen());
        }
    }

    private void assertNoTimeouts() {
        assertEquals("transaction timeouts on UAC", 0, shootist.getTimeoutEvents());
        assertEquals("transaction timeouts on B2B", 0, b2b.getTimeoutEvents());
        assertEquals("transaction timeouts on UAS", 0, shootme.getTimeoutEvents());
        if (shootme2 != null) {
            assertEquals("transaction timeouts on UAS2", 0, shootme2.getTimeoutEvents());
        }
    }

    private void assertNoDialogTimeouts() {
        assertTrue("dialog timeouts on UAC " + shootist.getDialogTimeoutReasons(),
                shootist.getDialogTimeoutReasons().isEmpty());
        assertTrue("dialog timeouts on B2B " + b2b.getDialogTimeoutReasons(), b2b.getDialogTimeoutReasons().isEmpty());
        assertTrue("dialog timeouts on UAS " + shootme.getDialogTimeoutReasons(),
                shootme.getDialogTimeoutReasons().isEmpty());
    }

    /** no transactions and no dialogs left anywhere */
    private void assertNothingLeft() throws Exception {
        boolean uacClean = AssertUntil.assertUntil(shootist.getNoTransactionsAssertion(), CLEANUP)
                && AssertUntil.assertUntil(shootist.getNoDialogsAssertion(), CLEANUP);
        assertTrue("left on UAC, client/server/dialogs " + shootist.getClientTransactionTableSize() + "/"
                + shootist.getServerTransactionTableSize() + "/" + shootist.getDialogCount(), uacClean);
        boolean b2bClean = AssertUntil.assertUntil(b2b.getNoTransactionsAssertion(), CLEANUP)
                && AssertUntil.assertUntil(b2b.getNoDialogsAssertion(), CLEANUP);
        assertTrue("left on B2B, client/server/dialogs " + b2b.getClientTransactionTableSize() + "/"
                + b2b.getServerTransactionTableSize() + "/" + b2b.getDialogCount(), b2bClean);
        boolean uasClean = AssertUntil.assertUntil(shootme.getNoTransactionsAssertion(), CLEANUP)
                && AssertUntil.assertUntil(shootme.getNoDialogsAssertion(), CLEANUP);
        assertTrue("left on UAS, client/server/dialogs " + shootme.getClientTransactionTableSize() + "/"
                + shootme.getServerTransactionTableSize() + "/" + shootme.getDialogCount(), uasClean);
        if (shootme2 != null) {
            boolean uas2Clean = AssertUntil.assertUntil(shootme2.getNoTransactionsAssertion(), CLEANUP)
                    && AssertUntil.assertUntil(shootme2.getNoDialogsAssertion(), CLEANUP);
            assertTrue("left on UAS2, client/server/dialogs " + shootme2.getClientTransactionTableSize() + "/"
                    + shootme2.getServerTransactionTableSize() + "/" + shootme2.getDialogCount(), uas2Clean);
        }
    }
}
