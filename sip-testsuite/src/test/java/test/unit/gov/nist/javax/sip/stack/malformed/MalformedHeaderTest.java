package test.unit.gov.nist.javax.sip.stack.malformed;

import java.util.List;

import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import gov.nist.javax.sip.stack.transports.processors.nio.NioMessageProcessorFactory;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.malformed.Records.MessageRecord;

/**
 * Malformed P-Preferred-Identity (no closing '>'): per RFC 3261 the stack ignores it and
 * passes the INVITE on, keeps the header for forwarding, leaves answering to the app
 */
public class MalformedHeaderTest {

    private static final int TIMEOUT = 5000;

    @Rule
    public ErrorCollector collector = new ErrorCollector();

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

    /** Netty, the stack default */
    @Test(timeout = 30000)
    public void testNettyMalformedPPreferredIdentity() throws Exception {
        checkMalformed(NettyMessageProcessorFactory.class);
    }

    /** NIO, UDP still goes through the OIO channel */
    @Test(timeout = 30000)
    public void testNioMalformedPPreferredIdentity() throws Exception {
        checkMalformed(NioMessageProcessorFactory.class);
    }

    private void checkMalformed(Class<?> transport) throws Exception {
        shootme = new Shootme(shootmePort, transport.getName());
        shootist = new Shootist(shootistPort, shootmePort);
        shootist.sendInvite(Shootist.MALFORMED_P_PREFERRED_IDENTITY);
        AssertUntil.assertUntil(shootist.getFinalInviteResponseAssertion(), TIMEOUT);

        // RFC 3261 8.2.2: the stack doesn't need the header, ignores it and goes on
        List<Throwable> parseErrors = shootme.getLoggedParseExceptions();
        check(parseErrors.isEmpty(), parseErrors.isEmpty() ? null : RecordingStackLogger.describe(parseErrors.get(0)));
        List<MessageRecord> invites = shootme.getReceivedInvites();
        check(!invites.isEmpty(), "Application got no event");
        if (!invites.isEmpty()) {
            MessageRecord invite = invites.get(0);
            check(invite.unparsedHeaders.contains(Shootist.MALFORMED_P_PREFERRED_IDENTITY),
                    "Malformed header not kept for the application");
            // RFC 3261 16.3: a forwarded request keeps the malformed header as is
            check(invite.encoded.contains(Shootist.MALFORMED_P_PREFERRED_IDENTITY),
                    "Malformed header dropped when the request is sent on");
        }

        // RFC 3261 8.2.2, 21.4.1: the application needs the identity, answers 400
        List<MessageRecord> finals = shootist.getFinalInviteResponses();
        if (finals.isEmpty()) {
            check(false, "No response arrived at the sender");
        } else {
            MessageRecord response = finals.get(0);
            check(response.status == Response.BAD_REQUEST,
                    "Sender got " + response.status + " " + response.reasonPhrase + " instead of 400 Bad Request");
        }
        check(!shootme.isIoExceptionSeen(), "IOException on UAS");
    }

    /** collected, all failures reported at the end */
    private void check(boolean ok, String failure) {
        if (!ok) {
            collector.addError(new AssertionError(failure));
        }
    }
}
