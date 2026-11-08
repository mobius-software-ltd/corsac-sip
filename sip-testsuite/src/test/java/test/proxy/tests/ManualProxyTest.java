package test.proxy.tests;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.header.CSeqHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;

/**
 * regular flow but no Proxy object, only branches
 */
public class ManualProxyTest extends TestCase {

    private static final int TIMEOUT = 15000;
    private static final long POLL_TIMEOUT = 5000;

    private Shootist caller;
    private ProxyUa proxy;
    private Shootme callee;

    @Override
    public void setUp() throws Exception {
        int callerPort = NetworkPortAssigner.retrieveNextPort();
        int proxyPort = NetworkPortAssigner.retrieveNextPort();
        int calleePort = NetworkPortAssigner.retrieveNextPort();

        caller = new Shootist(callerPort, proxyPort);
        caller.byeDelay = 300;
        proxy = new ProxyUa(proxyPort);
        callee = new Shootme(calleePort, Shootme.Behavior.ANSWER, 200);
    }

    public void testManualRegularFlow() throws Exception {
        caller.sendInvite();

        RequestEvent inviteEvent = proxy.pollRequest(POLL_TIMEOUT);
        assertNotNull("INVITE should reach the proxy", inviteEvent);
        assertEquals(Request.INVITE, inviteEvent.getRequest().getMethod());
        ServerTransaction inviteTransaction = proxy.ensureTransaction(inviteEvent);

        ProxyBranch branch = new ProxyBranch(proxy, inviteTransaction, inviteEvent.getRequest(),
                callee.getPort());
        branch.forward();

        ResponseEvent ringingEvent = proxy.pollResponseAbove100(POLL_TIMEOUT);
        assertNotNull("Branch should ring", ringingEvent);
        assertEquals(Response.RINGING, ringingEvent.getResponse().getStatusCode());
        proxy.relayResponse(ringingEvent);

        ResponseEvent okEvent = proxy.pollResponseAbove100(POLL_TIMEOUT);
        assertNotNull("Branch should answer", okEvent);
        assertEquals(Response.OK, okEvent.getResponse().getStatusCode());
        assertEquals(Request.INVITE,
                ((CSeqHeader) okEvent.getResponse().getHeader(CSeqHeader.NAME)).getMethod());
        proxy.relayResponse(okEvent);

        RequestEvent ackEvent = proxy.pollRequest(POLL_TIMEOUT);
        assertNotNull("ACK should traverse the proxy", ackEvent);
        assertEquals(Request.ACK, ackEvent.getRequest().getMethod());
        proxy.forwardInDialog(ackEvent.getRequest());

        RequestEvent byeEvent = proxy.pollRequest(POLL_TIMEOUT);
        assertNotNull("BYE should traverse the proxy", byeEvent);
        assertEquals(Request.BYE, byeEvent.getRequest().getMethod());
        proxy.forwardInDialog(byeEvent.getRequest());

        assertTrue("Caller should complete the call",
                AssertUntil.assertUntil(caller.getCompletedCallAssertion(), TIMEOUT));
        assertTrue("Callee should see the complete call",
                AssertUntil.assertUntil(callee.getCompletedCallAssertion(), TIMEOUT));
    }

    @Override
    public void tearDown() throws Exception {
        caller.stop();
        proxy.stop();
        callee.stop();
    }
}
