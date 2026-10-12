package test.unit.gov.nist.javax.sip.stack.retransmissiontimer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.sip.Transaction;
import javax.sip.TransactionState;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import gov.nist.core.executor.SIPTimer;
import gov.nist.javax.sip.stack.SIPTransactionImpl;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Make sure retransmission timers die with their transactions 
 * Timers shortened for this test
 */
public class RetransmissionTimerLeakTest {

    private static final int TIMEOUT = 10000;
    /** Timer F, D or H (3.2 s) plus margin */
    private static final int TERMINATION_TIMEOUT = 15000;
    /** over 3 x T2: a task that re-arms itself reschedules several times meanwhile */
    private static final long SETTLE = 1500;
    /** a 486 sent right before Timer H still on its way */
    private static final long DRAIN = 200;
    /** server transactions linger before leaving the table */
    private static final int TRANSACTION_CLEANUP = 20000;

    private int shootistPort;
    private int shootmePort;
    private int rawShootistPort;

    private Shootist shootist;
    private Shootme shootme;
    private RawShootist rawShootist;

    @Before
    public void setUp() throws Exception {
        shootistPort = NetworkPortAssigner.retrieveNextPort();
        shootmePort = NetworkPortAssigner.retrieveNextPort();
        rawShootistPort = NetworkPortAssigner.retrieveNextPort();
    }

    @After
    public void tearDown() throws Exception {
        if (shootist != null) {
            shootist.stop();
        }
        if (rawShootist != null) {
            rawShootist.stop();
        }
        if (shootme != null) {
            shootme.stop();
        }
    }

    /** NOTIFY answered 200 with no 100 Trying first: TRYING -> COMPLETED -> Timer K. */
    @Test(timeout = 60000)
    public void testNotifyAnsweredWithoutProvisional() throws Exception {
        shootme = new Shootme(shootmePort);
        shootme.finalResponse = Response.OK;
        shootist = new Shootist(shootistPort, shootmePort);

        shootist.sendNotify();
        assertTrue("NOTIFY must be answered 200",
                AssertUntil.assertUntil(shootist.getFinalResponseAssertion(Response.OK, Request.NOTIFY), TIMEOUT));
        assertEquals("200 must hit TRYING directly", 0, shootist.getProvisionalResponses(Request.NOTIFY).size());

        assertNoRetransmissionTimerAfterTermination(shootist.getClientTransaction());
        assertNoTransactionsLeft();
        assertNoIo();
    }

    /** NOTIFY to a subscriber that never answers: Timer F terminates the transaction. */
    @Test(timeout = 60000)
    public void testNotifyTimeout() throws Exception {
        shootme = new Shootme(shootmePort);
        shootme.finalResponse = 0;
        shootist = new Shootist(shootistPort, shootmePort);

        shootist.sendNotify();
        assertTrue("Timer F must fire", AssertUntil.assertUntil(shootist.getTimeoutAssertion(), TERMINATION_TIMEOUT));
        assertTrue("NOTIFY must be retransmitted before Timer F",
                shootme.getReceivedRequests(Request.NOTIFY).size() > 1);

        assertNoRetransmissionTimerAfterTermination(shootist.getClientTransaction());
        assertNoTransactionsLeft();
        assertNoIo();
    }

    /** INVITE answered 486 with no provisional first: CALLING -> COMPLETED -> Timer D. */
    @Test(timeout = 60000)
    public void testInviteRejectedWithoutProvisional() throws Exception {
        shootme = new Shootme(shootmePort);
        shootme.finalResponse = Response.BUSY_HERE;
        shootist = new Shootist(shootistPort, shootmePort);

        shootist.sendInvite();
        assertTrue("INVITE must be answered 486", AssertUntil
                .assertUntil(shootist.getFinalResponseAssertion(Response.BUSY_HERE, Request.INVITE), TIMEOUT));
        assertEquals("486 must hit CALLING directly", 0, shootist.getProvisionalResponses(Request.INVITE).size());

        // COMPLETED until Timer D retransmits nothing, the timer must already be off here
        Transaction ct = shootist.getClientTransaction();
        assertEquals(TransactionState.COMPLETED, ct.getState());
        assertFalse("retransmission timer still scheduled in COMPLETED", isRetransmissionTimerScheduled(ct));

        assertNoRetransmissionTimerAfterTermination(ct);
        assertNoTransactionsLeft();
        assertNoIo();
    }

    /** INVITE rejected 486 and never ACKed: Timer H terminates the server transaction, the 486 must stop going out. */
    @Test(timeout = 60000)
    public void testUnacknowledgedInviteRejection() throws Exception {
        shootme = new Shootme(shootmePort);
        shootme.finalResponse = Response.BUSY_HERE;
        rawShootist = new RawShootist(rawShootistPort, shootmePort);

        rawShootist.sendInvite();
        assertTrue("INVITE must be answered 486",
                AssertUntil.assertUntil(rawShootist.getBusyHereAssertion(), TIMEOUT));
        Transaction st = shootme.getServerTransaction();
        assertNotNull(st);
        assertTrue("Timer H must terminate the server transaction",
                AssertUntil.assertUntil(terminated(st), TERMINATION_TIMEOUT));

        Thread.sleep(DRAIN);
        int busyHereAtTermination = rawShootist.getBusyHereCount();
        assertTrue("486 must be retransmitted until Timer H", busyHereAtTermination > 1);
        Thread.sleep(SETTLE);
        assertEquals("486 retransmitted after the server transaction terminated", busyHereAtTermination,
                rawShootist.getBusyHereCount());
        assertFalse("retransmission timer still scheduled on a terminated transaction",
                isRetransmissionTimerScheduled(st));
        assertNoTransactionsLeft();
        assertFalse("IOException on UAS", shootme.isIoExceptionSeen());
    }

    /** A task re-armed concurrently with the cancel must stop by itself on a terminated transaction. */
    @Test(timeout = 60000)
    public void testRetransmissionTaskDoesNotRearmOnTerminatedTransaction() throws Exception {
        shootme = new Shootme(shootmePort);
        shootme.finalResponse = Response.OK;
        shootist = new Shootist(shootistPort, shootmePort);

        shootist.sendNotify();
        Transaction ct = shootist.getClientTransaction();
        assertTrue("transaction must terminate", AssertUntil.assertUntil(terminated(ct), TERMINATION_TIMEOUT));

        // what the race leaves behind: a task scheduled after the transaction terminated
        Method enableRetransmissionTimer = SIPTransactionImpl.class.getDeclaredMethod("enableRetransmissionTimer",
                int.class);
        enableRetransmissionTimer.setAccessible(true);
        enableRetransmissionTimer.invoke(ct, 1);

        Thread.sleep(SETTLE);
        assertFalse("retransmission timer re-armed itself on a terminated transaction",
                isRetransmissionTimerScheduled(ct));
        assertNoIo();
    }

    private void assertNoRetransmissionTimerAfterTermination(Transaction transaction) throws Exception {
        assertNotNull(transaction);
        assertTrue("transaction must terminate",
                AssertUntil.assertUntil(terminated(transaction), TERMINATION_TIMEOUT));
        Thread.sleep(SETTLE);
        assertFalse("retransmission timer still scheduled on a terminated transaction",
                isRetransmissionTimerScheduled(transaction));
    }

    private static TestAssertion terminated(final Transaction transaction) {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return transaction.getState() == TransactionState.TERMINATED;
            }
        };
    }

    /** retransmissionTimer is set and its task is still due (moved to Long.MAX_VALUE once ran or cancelled) */
    private static boolean isRetransmissionTimerScheduled(Transaction transaction) throws Exception {
        Field retransmissionTimer = SIPTransactionImpl.class.getDeclaredField("retransmissionTimer");
        retransmissionTimer.setAccessible(true);
        SIPStackTimerTask task = (SIPStackTimerTask) retransmissionTimer.get(transaction);
        if (task == null) {
            return false;
        }
        Method getSipTimerTask = SIPStackTimerTask.class.getDeclaredMethod("getSipTimerTask");
        getSipTimerTask.setAccessible(true);
        Object timer = getSipTimerTask.invoke(task);
        if (timer == null) {
            return false;
        }
        if (!(timer instanceof SIPTimer)) {
            throw new IllegalStateException("test expects MobiusSipTimer, got " + timer.getClass().getName());
        }
        return ((SIPTimer) timer).getRealTimestamp() != Long.MAX_VALUE;
    }

    private void assertNoIo() {
        assertFalse("IOException on UAC", shootist.isIoExceptionSeen());
        assertFalse("IOException on UAS", shootme.isIoExceptionSeen());
    }

    private void assertNoTransactionsLeft() throws Exception {
        if (shootist != null) {
            boolean uacClean = AssertUntil.assertUntil(shootist.getNoTransactionsAssertion(), TRANSACTION_CLEANUP);
            assertTrue("transactions left on UAC, client/server " + shootist.getClientTransactionTableSize() + "/"
                    + shootist.getServerTransactionTableSize(), uacClean);
        }
        boolean uasClean = AssertUntil.assertUntil(shootme.getNoTransactionsAssertion(), TRANSACTION_CLEANUP);
        assertTrue("transactions left on UAS, client/server " + shootme.getClientTransactionTableSize() + "/"
                + shootme.getServerTransactionTableSize(), uasClean);
    }
}
