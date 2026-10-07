package test.unit.gov.nist.javax.sip.stack.rfc4028;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sip.message.Request;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.rfc4028.Records.MessageRecord;

/**
 * RFC 4028 session timers in automatic mode: refresh at SE/2 = 45 s, expiry BYE
 * at SE - min(32, SE/3) = 60 s, SE = 90 everywhere (the stack minimum).
 */
public class RFC4028Test {

    private static final int TIMEOUT = 10000;
    /** SE/2 minus 5 s, no refresh yet */
    private static final long BEFORE_REFRESH = 40000;
    /** after the refresh, still ahead of the 60 s deadline */
    private static final long BEFORE_DEADLINE = 5000;
    /** 5 s ahead of the 60 s deadline, from the 2xx */
    private static final long BEFORE_EXPIRY = 55000;
    private static final int EXPIRY_TIMEOUT = 15000;
    /** past both the 45 s refresh and the 60 s deadline */
    private static final long QUIET = 65000;
    /** same, plus 5 s for a timer wrongly re-armed early in the dialog */
    private static final long TIMER_OFF_QUIET = 70000;
    /** when refresh transactions are supposed to already be gone after dialog */
    private static final int TRANSACTION_CLEANUP = 40000;

    private int shootistPort;
    private int shootmePort;
    private int proxyPort;

    private Shootist shootist;
    private Shootme shootme;
    private TestProxy proxy;

    @Before
    public void setUp() throws Exception {
        shootistPort = NetworkPortAssigner.retrieveNextPort();
        shootmePort = NetworkPortAssigner.retrieveNextPort();
        proxyPort = NetworkPortAssigner.retrieveNextPort();
    }

    @After
    public void tearDown() throws Exception {
        if (shootist != null) {
            shootist.stop();
        }
        if (proxy != null) {
            proxy.stop();
        }
        if (shootme != null) {
            shootme.stop();
        }
    }

    /** UAC refresher: re-INVITE at SE/2 with the original offer, two cycles. */
    @Test(timeout = 180000)
    public void testRefreshCycle() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uac";
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");
        assertTrue("200 must carry Require: timer", ok.requireTimer);

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertTrue("first refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));
        assertTrue("refresh must be answered 200", AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), TIMEOUT));

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no second refresh before SE/2 after the re-arm", 1, shootist.getStackRefreshes().size());
        assertTrue("second refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(2), TIMEOUT));
        assertTrue("second refresh must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(3), TIMEOUT));

        List<MessageRecord> refreshes = shootist.getStackRefreshes();
        assertEquals("exactly two refreshes " + refreshes, 2, refreshes.size());
        assertUacRefreshes(refreshes, shootist.getFirstSentInvite(), ok, Shootist.OFFER_SDP);

        List<MessageRecord> invites = shootme.getReceivedInvites();
        assertEquals("initial INVITE and both refreshes must reach the UAS", 3, invites.size());
        for (MessageRecord r : invites) {
            assertTrue("Supported: timer on every INVITE " + r, r.supportedTimer);
        }
        for (int i = 1; i < 3; i++) {
            assertEquals(Shootist.OFFER_SDP, invites.get(i).body);
            assertSessionExpires(invites.get(i), 90, "uac");
        }

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** UAS refresher: UAC stays quiet, UAS re-INVITEs at SE/2 with its own 2xx body. */
    @Test(timeout = 180000)
    public void testRefreshCycleUasRefresher() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uas";
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uas");
        assertTrue("200 with refresher=uas must still carry Require: timer", ok.requireTimer);

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no UAS refresh before SE/2", 0, shootist.getReceivedInvites().size());
        assertEquals("UAC must not refresh", 0, shootist.getStackRefreshes().size());
        assertTrue("first UAS refresh at SE/2",
                AssertUntil.assertUntil(shootist.getReceivedInvitesAssertion(1), TIMEOUT));
        assertTrue("UAS must get the 200 to its refresh",
                AssertUntil.assertUntil(shootme.getInviteOksAssertion(1), TIMEOUT));

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no second UAS refresh before SE/2 after the re-arm", 1, shootist.getReceivedInvites().size());
        assertTrue("second UAS refresh at SE/2",
                AssertUntil.assertUntil(shootist.getReceivedInvitesAssertion(2), TIMEOUT));
        assertTrue("UAS must get the 200 to its second refresh",
                AssertUntil.assertUntil(shootme.getInviteOksAssertion(2), TIMEOUT));

        assertEquals("UAC must not refresh when the UAS is the refresher " + shootist.getStackRefreshes(), 0,
                shootist.getStackRefreshes().size());
        List<MessageRecord> peerRefreshes = shootist.getReceivedInvites();
        assertEquals("UAS must have refreshed twice " + peerRefreshes, 2, peerRefreshes.size());
        for (MessageRecord r : peerRefreshes) {
            assertEquals("UAS re-INVITE body must be its own 200 body", Shootme.OK_ANSWER_SDP, r.body);
            assertSessionExpires(r, 90, "uac");
        }
        assertEquals(2, shootme.getStackRefreshes().size());
        assertUasRefreshes(shootme.getStackRefreshes(), shootme.getReceivedInvites().get(0),
                shootme.getFirstSentInviteOk(), shootme.getViaSentBy(), Shootme.OK_ANSWER_SDP);

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft(); 
    }

    /** Early UPDATE with a new offer in a 183/PRACK dialog: the refresh re-INVITE carries that offer, the last SDP we sent. */
    @Test(timeout = 180000)
    public void testEarlyUpdateSdpUsedForRefresh() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uac";
        shootme.sendReliableProvisional = true;
        // 2xx well after the PRACK so the early UPDATE's 2xx cannot race it
        shootme.okDelay = 1500;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.requireReliableProvisionalResponse = true;
        shootist.sendUpdate = true;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("early UPDATE must be answered 200", AssertUntil.assertUntil(shootist.getUpdateOkAssertion(), TIMEOUT));

        assertTrue("reliable provisional response expected", shootist.getReliableProvisionalCount() >= 1);
        assertTrue("200 to PRACK expected", shootist.getPrackOkCount() >= 1);
        List<MessageRecord> appRequests = shootist.getAppRequests();
        assertEquals("one early UPDATE " + appRequests, 1, appRequests.size());
        assertEquals(Request.UPDATE, appRequests.get(0).method);
        assertEquals("early UPDATE carries the new offer", Shootist.EARLY_UPDATE_SDP, appRequests.get(0).body);
        assertFalse("early UPDATE is sent without Session-Expires", appRequests.get(0).hasSessionExpires());
        assertEquals("UAS answers the early UPDATE offer", Shootme.UPDATE_ANSWER_SDP, shootist.getUpdateOks().get(0).body);

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertTrue("first refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));
        assertTrue("refresh must be answered 200", AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), TIMEOUT));

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no second refresh before SE/2 after the re-arm", 1, shootist.getStackRefreshes().size());
        assertTrue("second refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(2), TIMEOUT));
        assertTrue("second refresh must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(3), TIMEOUT));

        List<MessageRecord> refreshes = shootist.getStackRefreshes();
        assertEquals("exactly two refreshes " + refreshes, 2, refreshes.size());
        // the early UPDATE was our last target refresh: the peer has its Contact, the refresh must carry that one
        assertUacRefreshes(refreshes, appRequests.get(0), ok, Shootist.EARLY_UPDATE_SDP);
        
        List<MessageRecord> invites = shootme.getReceivedInvites();
        assertEquals("initial INVITE and two refreshes must reach the UAS", 3, invites.size());
        assertEquals("re-INVITE body must be the early UPDATE offer, not the PRACK answer", Shootist.EARLY_UPDATE_SDP,
                invites.get(1).body);
        assertEquals(Shootist.EARLY_UPDATE_SDP, invites.get(2).body);
        assertEquals("only the early UPDATE reaches the UAS as UPDATE", 1, shootme.getReceivedUpdates().size());

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** 183/PRACK, body-less 2xx: refresh carries the PRACK answer; UAS drops it, UAC BYEs at 60 s. */
    @Test(timeout = 150000)
    public void testPrackAndExpiry() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.MANUAL);
        shootme.refresher = "uac";
        shootme.sendReliableProvisional = true;
        shootme.dropRefresh = true;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.requireReliableProvisionalResponse = true;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        assertTrue("reliable provisional response expected", shootist.getReliableProvisionalCount() >= 1);
        assertTrue("200 to PRACK expected", shootist.getPrackOkCount() >= 1);
        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");
        assertTrue(ok.requireTimer);

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertTrue("refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));

        List<MessageRecord> refreshes = shootist.getStackRefreshes();
        assertUacRefreshes(refreshes, shootist.getFirstSentInvite(), ok, Shootist.PRACK_ANSWER_SDP);
        List<MessageRecord> invites = shootme.getReceivedInvites();
        assertEquals(2, invites.size());
        assertEquals(Shootist.PRACK_ANSWER_SDP, invites.get(1).body);
        assertTrue(invites.get(1).supportedTimer);

        // refresh never answered: BYE at the deadline, not before
        Thread.sleep(BEFORE_DEADLINE);
        assertEquals("no BYE before the deadline", 0, shootist.getSentByes().size());
        assertFalse("no SessionExpired before the deadline", shootist.isSessionExpiredSeen());
        assertTrue("UAC must BYE at the deadline and get 200",
                AssertUntil.assertUntil(shootist.getByeOkAssertion(), EXPIRY_TIMEOUT));
        assertTrue("DialogTimeoutEvent(SessionExpired) expected",
                AssertUntil.assertUntil(shootist.getSessionExpiredAssertion(), TIMEOUT));
     // the refresh got the stack's automatic 100, so at expiry it is PROCEEDING: it must be CANCELed, not left
     // to retransmit until Timer B (17 s later)
     assertTrue("pending refresh must be CANCELed at expiry and closed with 487, CANCELs/487s: "
             + shootist.getSentCancels().size() + "/" + shootist.getInvite487s().size(),
             AssertUntil.assertUntil(shootist.getRefreshCancelledAssertion(), TIMEOUT));
        assertEquals("exactly one BYE " + shootist.getSentByes(), 1, shootist.getSentByes().size());
        assertEquals("exactly one refresh " + shootist.getStackRefreshes(), 1, shootist.getStackRefreshes().size());
        assertTrue("Should see invite, ACK and BYE",
                AssertUntil.assertUntil(shootme.getCompletedCallAssertion(), TIMEOUT));
        assertEquals("hand-rolled UAS never BYEs", 0, shootme.getSentByes().size());
        assertFalse(shootist.isByeReceived());
        assertNoIo();
    }

    /** UAS claims refresher and never refreshes: the UAC refreshee BYEs at 60 s. */
    @Test(timeout = 150000)
    public void testPrackAndExpiryUasRefresher() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.MANUAL);
        shootme.refresher = "uas";
        shootme.sendReliableProvisional = true;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.requireReliableProvisionalResponse = true;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        assertTrue("reliable provisional response expected", shootist.getReliableProvisionalCount() >= 1);
        assertTrue("200 to PRACK expected", shootist.getPrackOkCount() >= 1);
        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uas");
        assertTrue(ok.requireTimer);

        Thread.sleep(BEFORE_EXPIRY);
        assertEquals("refreshee must not refresh " + shootist.getStackRefreshes(), 0,
                shootist.getStackRefreshes().size());
        assertEquals("hand-rolled UAS never refreshes", 0, shootist.getReceivedInvites().size());
        assertEquals("hand-rolled UAS never refreshes", 0, shootist.getReceivedUpdates().size());
        assertEquals("no BYE before the deadline", 0, shootist.getSentByes().size());
        assertFalse("no SessionExpired before the deadline", shootist.isSessionExpiredSeen());

        assertTrue("refreshee must BYE at the deadline and get 200",
                AssertUntil.assertUntil(shootist.getByeOkAssertion(), EXPIRY_TIMEOUT));
        assertTrue("DialogTimeoutEvent(SessionExpired) expected",
                AssertUntil.assertUntil(shootist.getSessionExpiredAssertion(), TIMEOUT));

        assertEquals("exactly one BYE " + shootist.getSentByes(), 1, shootist.getSentByes().size());
        assertEquals(0, shootist.getStackRefreshes().size());
        assertEquals(1, shootme.getReceivedInvites().size());
        assertTrue("Should see invite, ACK and BYE",
                AssertUntil.assertUntil(shootme.getCompletedCallAssertion(), TIMEOUT));
        assertEquals("hand-rolled UAS never BYEs", 0, shootme.getSentByes().size());
        assertFalse(shootist.isByeReceived());
        assertNoIo();
    }

    /** SE=30 on INVITE and on a mid-dialog UPDATE: 422 + Min-SE 90 from the stack, retry with CSeq + 1. */
    @Test(timeout = 60000)
    public void testSessionIntervalTooSmall() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uac";
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.smallSe = true;
        shootist.midDialogUpdateDelay = 5000;

        shootist.sendInvite();
        assertTrue("retried INVITE must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        assertEquals("INVITE with SE=30 must be rejected once", 1, shootist.getRejected422Count());
        assertEquals("422 must announce Min-SE: 90", 90, shootist.getMinSeOffered());
        List<MessageRecord> invite422s = shootist.getInvite422s();
        assertEquals(1, invite422s.size());
        assertEquals(Integer.valueOf(90), invite422s.get(0).minSe);
        assertEquals("rejected INVITE must not reach the UAS application", 1, shootme.getReceivedInvites().size());

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertEquals("retry is CSeq 2", 2, ok.cseq);
        assertSessionExpires(ok, 90, "uac");
        assertTrue(ok.requireTimer);
        MessageRecord retried = shootme.getReceivedInvites().get(0);
        assertEquals(2, retried.cseq);
        assertEquals(Integer.valueOf(90), retried.seExpires);
        assertEquals("retry must carry Min-SE", Integer.valueOf(90), retried.minSe);

        assertTrue("mid-dialog UPDATE with SE=30 must be rejected with 422",
                AssertUntil.assertUntil(shootist.getUpdate422Assertion(), TIMEOUT));
        List<MessageRecord> update422s = shootist.getUpdate422s();
        assertEquals(1, update422s.size());
        assertEquals(Integer.valueOf(90), update422s.get(0).minSe);
        assertEquals("rejected UPDATE must not reach the UAS application", 0, shootme.getReceivedUpdates().size());
        assertFalse(shootist.isUpdateOkSeen());
        assertEquals(0, shootist.getStackRefreshes().size());

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** 200 to the refresh without Session-Expires turns the timer off. */
    @Test(timeout = 180000)
    public void testTimerOffMidDialog() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.MANUAL);
        shootme.refresher = "uac";
        shootme.echoSeOnRefresh = false;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");
        assertTrue(ok.requireTimer);

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertTrue("refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));
        assertTrue("refresh must be answered 200", AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), TIMEOUT));

        List<MessageRecord> oks = shootist.getInviteOks();
        assertFalse("200 to the refresh must have no Session-Expires", oks.get(1).hasSessionExpires());
        assertFalse(oks.get(1).requireTimer);
        assertUacRefreshes(shootist.getStackRefreshes(), shootist.getFirstSentInvite(), ok, Shootist.OFFER_SDP);

        // past the old deadline and past where the second refresh would have been
        Thread.sleep(TIMER_OFF_QUIET);
        assertEquals("exactly one refresh, none after the SE-less 200 " + shootist.getStackRefreshes(), 1,
                shootist.getStackRefreshes().size());
        assertEquals("no BYE from UAC", 0, shootist.getSentByes().size());
        assertFalse("no BYE from UAS", shootist.isByeReceived());
        assertFalse(shootist.isSessionExpiredSeen());
        assertEquals(2, shootme.getReceivedInvites().size());

        finishCall();
        assertNoIo();
    }

    /** UAC flag off, no Session-Expires anywhere, nothing happens. */
    @Test(timeout = 120000)
    public void testNoTimer() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.MANUAL);
        shootist.sessionExpires = 0;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        List<MessageRecord> invites = shootme.getReceivedInvites();
        assertEquals(1, invites.size());
        assertFalse("stack flag off: no Supported: timer", invites.get(0).supportedTimer);
        assertFalse("stack flag off: no Session-Expires", invites.get(0).hasSessionExpires());
        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertFalse("UAS must not add Session-Expires when none was requested", ok.hasSessionExpires());
        assertFalse(ok.requireTimer);

        Thread.sleep(QUIET);
        assertEquals("no refresh from UAC", 0, shootist.getStackRefreshes().size());
        assertEquals("no refresh from UAS", 0, shootme.getStackRefreshes().size());
        assertEquals("no BYE from UAC", 0, shootist.getSentByes().size());
        assertEquals(0, shootist.getTimeoutEvents());

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** ACK held 1.5 s so the 2xx is retransmitted: one refresh per cycle, SDP survives the re-arms. */
    @Test(timeout = 180000)
    public void testRetransmittedOk() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uac";
        shootme.sendReliableProvisional = true;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.requireReliableProvisionalResponse = true;
        shootist.delayAck = true;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");
        assertTrue("200 to the INVITE must be retransmitted while the ACK is held",
                AssertUntil.assertUntil(shootist.getInviteOkCopiesAssertion(ok.cseq, 2), TIMEOUT));

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertTrue("first refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));
        assertTrue("refresh must be answered 200", AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), TIMEOUT));
        MessageRecord refreshOk = shootist.getInviteOks().get(1);
        assertEquals(shootist.getStackRefreshes().get(0).cseq, refreshOk.cseq);
        assertTrue("200 to the refresh must be retransmitted while the ACK is held",
                AssertUntil.assertUntil(shootist.getInviteOkCopiesAssertion(refreshOk.cseq, 2), TIMEOUT));

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("exactly one refresh per cycle, retransmitted 2xx must not add any", 1,
                shootist.getStackRefreshes().size());
        assertTrue("second refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(2), TIMEOUT));
        assertTrue("second refresh must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(3), TIMEOUT));

        List<MessageRecord> refreshes = shootist.getStackRefreshes();
        assertEquals("exactly one refresh per cycle " + refreshes, 2, refreshes.size());
        // SDP must survive the re-arm on every retransmitted 200
        assertUacRefreshes(refreshes, shootist.getFirstSentInvite(), ok, Shootist.PRACK_ANSWER_SDP);
        List<MessageRecord> invites = shootme.getReceivedInvites();
        assertEquals(3, invites.size());
        assertEquals(Shootist.PRACK_ANSWER_SDP, invites.get(1).body);
        assertEquals(Shootist.PRACK_ANSWER_SDP, invites.get(2).body);

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** 408 to the refresh: BYE right away, not at the deadline. */
    @Test(timeout = 120000)
    public void testRefreshRejectedWith408() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.MANUAL);
        shootme.refresher = "uac";
        shootme.refreshResponse = 408;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertEquals("no BYE before the refresh", 0, shootist.getSentByes().size());
        assertTrue("refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));

        // refresh seen by 50.5 s at the latest, so this wait ends before the 60 s deadline: only the 408 can satisfy it
        assertTrue("UAC must BYE right after the 408 and get 200",
                AssertUntil.assertUntil(shootist.getByeOkAssertion(), (int) BEFORE_DEADLINE));
        assertTrue("DialogTimeoutEvent(SessionExpired) expected",
                AssertUntil.assertUntil(shootist.getSessionExpiredAssertion(), TIMEOUT));

        assertEquals("exactly one 408", 1, shootist.getInvite408s().size());
        assertEquals("exactly one BYE " + shootist.getSentByes(), 1, shootist.getSentByes().size());
        assertEquals("exactly one refresh " + shootist.getStackRefreshes(), 1, shootist.getStackRefreshes().size());
        assertUacRefreshes(shootist.getStackRefreshes(), shootist.getFirstSentInvite(), ok, Shootist.OFFER_SDP);
        assertEquals(2, shootme.getReceivedInvites().size());
        assertTrue("Should see invite, ACK and BYE",
                AssertUntil.assertUntil(shootme.getCompletedCallAssertion(), TIMEOUT));
        assertEquals("hand-rolled UAS never BYEs", 0, shootme.getSentByes().size());
        assertFalse(shootist.isByeReceived());
        assertNoIo();
    }

    /** UAS without timer support answers without Session-Expires: no timer at all. */
    @Test(timeout = 120000)
    public void testPeerWithoutTimerSupport() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.MANUAL);
        // refresher stays null, no Session-Expires on the 2xx
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord invite = shootme.getReceivedInvites().get(0);
        assertTrue("INVITE did request a timer", invite.hasSessionExpires());
        assertTrue(invite.supportedTimer);
        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertFalse(ok.hasSessionExpires());
        assertFalse(ok.requireTimer);

        Thread.sleep(QUIET);
        assertEquals("no refresh without Session-Expires in the 2xx", 0, shootist.getStackRefreshes().size());
        assertEquals("no BYE from UAC", 0, shootist.getSentByes().size());
        assertEquals(0, shootist.getTimeoutEvents());
        assertEquals(1, shootme.getReceivedInvites().size());

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** App re-INVITE without Session-Expires turns the timer off on both sides. */
    @Test(timeout = 150000)
    public void testAppReInviteWithoutSeTurnsTimerOff() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uac";
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.appReInviteDelay = 5000;
        shootist.appReInviteWithSe = false;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));
        assertTrue("app re-INVITE must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), TIMEOUT));

        List<MessageRecord> appRequests = shootist.getAppRequests();
        assertEquals(1, appRequests.size());
        assertEquals(Request.INVITE, appRequests.get(0).method);
        assertFalse("app re-INVITE goes out without Session-Expires", appRequests.get(0).hasSessionExpires());
        assertEquals(Shootist.REINVITE_OFFER_SDP, appRequests.get(0).body);

        List<MessageRecord> oks = shootist.getInviteOks();
        assertEquals(2, oks.size());
        assertSessionExpires(oks.get(0), 90, "uac");
        assertFalse("UAS must not add Session-Expires to a 2xx for a request without one",
                oks.get(1).hasSessionExpires());
        assertFalse(oks.get(1).requireTimer);

        Thread.sleep(TIMER_OFF_QUIET);
        assertEquals("no refresh once the timer is off " + shootist.getStackRefreshes(), 0,
                shootist.getStackRefreshes().size());
        assertEquals("no refresh from UAS either", 0, shootme.getStackRefreshes().size());
        assertEquals("no BYE from UAC", 0, shootist.getSentByes().size());
        assertEquals(2, shootme.getReceivedInvites().size());

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** App re-INVITE at 40 s answered at 50 s: the 45 s refresh is skipped, next one at 95 s with the new offer. */
    @Test(timeout = 180000)
    public void testRefreshSkippedWhileReInviteInFlight() throws Exception {
        final long appReInviteDelay = 40000;
        final long reInviteAnswerDelay = 10000;
        shootme = new Shootme(shootmePort, SessionTimerMode.STACK);
        shootme.refresher = "uac";
        shootme.reInviteAnswerDelay = reInviteAnswerDelay;
        shootist = new Shootist(shootistPort, shootmePort, SessionTimerMode.STACK);
        shootist.appReInviteDelay = appReInviteDelay;
        shootist.appReInviteWithSe = true;

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200", AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        Thread.sleep(appReInviteDelay - BEFORE_DEADLINE);
        assertEquals(0, shootist.getAppRequests().size());
        assertEquals(0, shootist.getStackRefreshes().size());
        assertTrue("app re-INVITE at 40 s", AssertUntil.assertUntil(shootist.getAppRequestsAssertion(1), TIMEOUT));
        List<MessageRecord> appRequests = shootist.getAppRequests();
        assertEquals(Request.INVITE, appRequests.get(0).method);
        assertEquals(Shootist.REINVITE_OFFER_SDP, appRequests.get(0).body);
        assertEquals(Integer.valueOf(90), appRequests.get(0).seExpires);

        // 2xx at 50 s, the 45 s refresh must have been skipped meanwhile
        assertTrue("delayed 200 to the app re-INVITE",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), (int) reInviteAnswerDelay + TIMEOUT));
        assertEquals("no refresh while the app re-INVITE was pending " + shootist.getStackRefreshes(), 0,
                shootist.getStackRefreshes().size());
        MessageRecord reInviteOk = shootist.getInviteOks().get(1);
        assertEquals(appRequests.get(0).cseq, reInviteOk.cseq);
        assertSessionExpires(reInviteOk, 90, "uac");
        assertTrue(reInviteOk.requireTimer);

        // re-anchored on that 2xx
        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2 after the re-arm", 0, shootist.getStackRefreshes().size());
        assertTrue("refresh at SE/2 after the 2xx to the app re-INVITE",
                AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));
        // the UAS delays every re-INVITE answer, the refresh included
        assertTrue("refresh must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(3), (int) reInviteAnswerDelay + TIMEOUT));

        List<MessageRecord> refreshes = shootist.getStackRefreshes();
        assertEquals(1, refreshes.size());
        // the latest offer, and the dialog as the app re-INVITE's 2xx left it
        assertUacRefreshes(refreshes, appRequests.get(0), reInviteOk, Shootist.REINVITE_OFFER_SDP);
        assertEquals(3, shootme.getReceivedInvites().size());

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
    }

    /** Proxy inserts SE + Require: timer into the timerless UAS's 2xx, on the refreshes too. */
    @Test(timeout = 180000)
    public void testProxyInsertsSessionExpiresForTimerlessUas() throws Exception {
        shootme = new Shootme(shootmePort, SessionTimerMode.MANUAL);
        shootme.echoSeOnRefresh = false;
        proxy = new TestProxy(proxyPort, shootmePort);
        shootist = new Shootist(shootistPort, proxyPort, SessionTimerMode.STACK);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 200 through the proxy",
                AssertUntil.assertUntil(shootist.getInviteOkAssertion(), TIMEOUT));

        MessageRecord ok = shootist.getFirstInviteOk();
        assertNotNull(ok);
        assertSessionExpires(ok, 90, "uac");
        assertTrue(ok.requireTimer);

        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no refresh before SE/2", 0, shootist.getStackRefreshes().size());
        assertTrue("first refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(1), TIMEOUT));
        assertTrue("refresh must be answered 200", AssertUntil.assertUntil(shootist.getInviteOksAssertion(2), TIMEOUT));

        // only happens if the proxy inserted SE into the 2xx to the first refresh
        Thread.sleep(BEFORE_REFRESH);
        assertEquals("no second refresh before SE/2 after the re-arm", 1, shootist.getStackRefreshes().size());
        assertTrue("second refresh at SE/2", AssertUntil.assertUntil(shootist.getStackRefreshesAssertion(2), TIMEOUT));
        assertTrue("second refresh must be answered 200",
                AssertUntil.assertUntil(shootist.getInviteOksAssertion(3), TIMEOUT));

        List<MessageRecord> invites = shootme.getReceivedInvites();
        assertEquals("initial INVITE and two refreshes must reach the UAS", 3, invites.size());
        for (MessageRecord r : invites) {
            assertTrue(r.supportedTimer);
            assertEquals(Integer.valueOf(90), r.seExpires);
        }
        assertSessionExpires(invites.get(1), 90, "uac");
        assertSessionExpires(invites.get(2), 90, "uac");

        // as handed to the proxy application: SE + Require already there
        List<MessageRecord> upstreamOks = proxy.getUpstreamInviteOks();
        assertEquals("one 2xx per INVITE through the proxy", 3, upstreamOks.size());
        for (MessageRecord r : upstreamOks) {
            assertSessionExpires(r, 90, "uac");
            assertTrue("proxy branch must insert Require: timer: " + r, r.requireTimer);
        }
        assertEquals(3, proxy.getForwardedInvites().size());

        List<MessageRecord> refreshes = shootist.getStackRefreshes();
        assertEquals("exactly two refreshes " + refreshes, 2, refreshes.size());
        // through a record-routing proxy: the refresh must carry its Route
        assertFalse("200 must carry the proxy's Record-Route " + ok, ok.recordRoutes.isEmpty());
        assertUacRefreshes(refreshes, shootist.getFirstSentInvite(), ok, Shootist.OFFER_SDP);
        List<MessageRecord> oks = shootist.getInviteOks();
        assertEquals(3, oks.size());
        for (MessageRecord r : oks) {
            assertSessionExpires(r, 90, "uac");
            assertTrue(r.requireTimer);
        }

        finishCall();
        assertQuiet();
        assertNoTransactionsLeft();
        assertFalse(proxy.isIoExceptionSeen());
    }

    private static void assertSessionExpires(MessageRecord record, int expires, String refresher) {
        assertTrue("Session-Expires expected on " + record, record.hasSessionExpires());
        assertEquals("Session-Expires value on " + record, Integer.valueOf(expires), record.seExpires);
        assertEquals("refresher on " + record, refresher, record.seRefresher);
    }

    /** UAC refresher: dialog state is our INVITE (or app re-INVITE) and the 200 that answered it. */
    private static void assertUacRefreshes(List<MessageRecord> refreshes, MessageRecord invite, MessageRecord ok,
            String body) {
        assertNotNull(invite);
        assertNotNull(ok);
        List<String> routeSet = new ArrayList<String>(ok.recordRoutes);
        Collections.reverse(routeSet);
        MessageRecord previous = invite;
        for (MessageRecord r : refreshes) {
            assertRefreshRequest(r, previous, invite.callId, invite.fromUri, invite.fromTag, invite.toUri, ok.toTag,
                    ok.contact, invite.contact, routeSet, invite.viaSentBy, body);
            previous = r;
        }
    }

    /** UAS refresher: dialog state is the INVITE we received and the 200 we sent to it. */
    private static void assertUasRefreshes(List<MessageRecord> refreshes, MessageRecord invite, MessageRecord ok,
            String viaSentBy, String body) {
        assertNotNull(invite);
        assertNotNull(ok);
        MessageRecord previous = null;
        for (MessageRecord r : refreshes) {
            assertRefreshRequest(r, previous, invite.callId, invite.toUri, ok.toTag, invite.fromUri, invite.fromTag,
                    invite.contact, ok.contact, invite.recordRoutes, viaSentBy, body);
            previous = r;
        }
    }

    /**
     * A stack refresh must be a complete in-dialog re-INVITE: RFC 3261 12.2.1.1 dialog bits, our own Contact (a
     * re-INVITE is a target refresh), the RFC 4028 bits, and the last SDP we sent. previous = the INVITE we sent before
     * it on this dialog, null if none.
     */
    private static void assertRefreshRequest(MessageRecord refresh, MessageRecord previous, String callId,
            String fromUri, String fromTag, String toUri, String toTag, String remoteTarget, String ourContact,
            List<String> routeSet, String viaSentBy, String body) {
        assertEquals("refresh method " + refresh, Request.INVITE, refresh.method);
        assertEquals("Call-ID on " + refresh, callId, refresh.callId);
        assertEquals("From URI on " + refresh, fromUri, refresh.fromUri);
        assertEquals("From tag on " + refresh, fromTag, refresh.fromTag);
        assertEquals("To URI on " + refresh, toUri, refresh.toUri);
        assertEquals("To tag on " + refresh, toTag, refresh.toTag);
        assertEquals("Request-URI must be the remote target on " + refresh, remoteTarget, refresh.requestUri);
        assertEquals("Contact must be ours, not the listening point default, on " + refresh, ourContact,
                refresh.contact);
        assertEquals("Route set on " + refresh, routeSet, refresh.routes);
        assertEquals("Via sent-by on " + refresh, viaSentBy, refresh.viaSentBy);
        assertNotNull("Via branch on " + refresh, refresh.viaBranch);
        assertTrue("RFC 3261 branch on " + refresh, refresh.viaBranch.startsWith("z9hG4bK"));
        if (previous != null) {
            assertTrue("CSeq must grow, " + previous + " then " + refresh, refresh.cseq > previous.cseq);
            assertFalse("new transaction, new branch on " + refresh, refresh.viaBranch.equals(previous.viaBranch));
        }
        assertNotNull("Max-Forwards on " + refresh, refresh.maxForwards);
        assertSessionExpires(refresh, 90, "uac");
        assertEquals("Min-SE on " + refresh, Integer.valueOf(90), refresh.minSe);
        assertTrue("Supported: timer on " + refresh, refresh.supportedTimer);
        assertFalse("refresh must not require 100rel " + refresh, refresh.require100rel);
        assertEquals("Content-Type on " + refresh, "application/sdp", refresh.contentType);
        assertEquals("body on " + refresh, body, refresh.body);
        assertEquals("Content-Length on " + refresh, body.getBytes().length, refresh.contentLength);
    }

    /** app BYE from the UAC, both sides see the call through */
    private void finishCall() throws Exception {
        shootist.sendBye();
        assertTrue("Should see BYE response for ACKED Dialog and InviteOK seen",
                AssertUntil.assertUntil(shootist.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Should see invite, ACK and BYE",
                AssertUntil.assertUntil(shootme.getCompletedCallAssertion(), TIMEOUT));
    }

    /** no expiry, no BYE from the UAS, no transport errors */
    private void assertQuiet() {
        assertFalse("unexpected SessionExpired on UAC", shootist.isSessionExpiredSeen());
        assertFalse("unexpected SessionExpired on UAS", shootme.isSessionExpiredSeen());
        assertEquals("UAS must not BYE " + shootme.getSentByes(), 0, shootme.getSentByes().size());
        assertFalse("UAC must not receive a BYE", shootist.isByeReceived());
        assertNoIo();
    }

    private void assertNoIo() {
        assertFalse("IOException on UAC", shootist.isIoExceptionSeen());
        assertFalse("IOException on UAS", shootme.isIoExceptionSeen());
    }
    
    private void assertNoTransactionsLeft() throws Exception {
        boolean uacClean = AssertUntil.assertUntil(shootist.getNoTransactionsAssertion(), TRANSACTION_CLEANUP);
        assertTrue("transactions left on UAC, client/server " + shootist.getClientTransactionTableSize() + "/"
                + shootist.getServerTransactionTableSize(), uacClean);
        boolean uasClean = AssertUntil.assertUntil(shootme.getNoTransactionsAssertion(), TRANSACTION_CLEANUP);
        assertTrue("transactions left on UAS, client/server " + shootme.getClientTransactionTableSize() + "/"
                + shootme.getServerTransactionTableSize(), uasClean);
        if (proxy != null) {
            boolean proxyClean = AssertUntil.assertUntil(proxy.getNoTransactionsAssertion(), TRANSACTION_CLEANUP);
            assertTrue("transactions left on proxy, client/server " + proxy.getClientTransactionTableSize() + "/"
                    + proxy.getServerTransactionTableSize(), proxyClean);
        }
    }
}
