package test.unit.gov.nist.javax.sip.stack.malformed;

import java.text.ParseException;
import java.util.List;
import java.util.ListIterator;

import javax.sip.header.ExpiresHeader;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.NettyMessageParser;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import gov.nist.javax.sip.stack.transports.processors.nio.NioMessageProcessorFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.malformed.Records.MessageRecord;

/**
 * Malformed P-Preferred-Identity (no closing '>'): per RFC 3261 the stack ignores it and
 * passes the INVITE on, keeps the header for forwarding, leaves answering to the app
 * <p>
 * The other flows cover Netty TCP, a broken From and a proxy in between, the tests after them feed the Netty parser directly
 */
public class MalformedHeaderTest {

    private static final int TIMEOUT = 5000;

    private int shootistPort;
    private int shootmePort;
    private int proxyPort;

    private Shootist shootist;
    private Shootme shootme;
    private Proxy proxy;

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
        if (shootme != null) {
            shootme.stop();
        }
        if (proxy != null) {
            proxy.stop();
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

    /** Netty TCP */
    @Test(timeout = 30000)
    public void testNettyTcpMalformedPPreferredIdentity() throws Exception {
        checkMalformed(NettyMessageProcessorFactory.class, "tcp");
    }

    private void checkMalformed(Class<?> transport) throws Exception {
        checkMalformed(transport, "udp");
    }

    private void checkMalformed(Class<?> transport, String protocol) throws Exception {
        shootme = new Shootme(shootmePort, transport.getName(), protocol);
        shootist = new Shootist(shootistPort, shootmePort, protocol);
        shootist.sendInvite(Shootist.MALFORMED_P_PREFERRED_IDENTITY);
        AssertUntil.assertUntil(shootist.getFinalInviteResponseAssertion(), TIMEOUT);

        // RFC 3261 8.2.2: the stack doesn't need the header, ignores it and goes on
        List<Throwable> parseErrors = shootme.getLoggedParseExceptions();
        if (!parseErrors.isEmpty()) {
            Assert.fail(RecordingStackLogger.describe(parseErrors.get(0)));
        }
        List<MessageRecord> invites = shootme.getReceivedInvites();
        Assert.assertFalse("Application got no event", invites.isEmpty());
        MessageRecord invite = invites.get(0);
        Assert.assertTrue("Malformed header not kept for the application",
                invite.unparsedHeaders.contains(Shootist.MALFORMED_P_PREFERRED_IDENTITY));
        // RFC 3261 16.3: a forwarded request keeps the malformed header as is
        Assert.assertTrue("Malformed header dropped when the request is sent on",
                invite.encoded.contains(Shootist.MALFORMED_P_PREFERRED_IDENTITY));

        // RFC 3261 8.2.2, 21.4.1: the application needs the identity, answers 400
        List<MessageRecord> finals = shootist.getFinalInviteResponses();
        Assert.assertFalse("No response arrived at the sender", finals.isEmpty());
        MessageRecord response = finals.get(0);
        Assert.assertEquals("Sender got " + response.status + " " + response.reasonPhrase + " instead of 400 Bad Request",
                Response.BAD_REQUEST, response.status);
        Assert.assertFalse("IOException on UAS", shootme.isIoExceptionSeen());
    }

    /** Netty UDP, From without '>' */
    @Test(timeout = 30000)
    public void testNettyMalformedFrom() throws Exception {
        checkMalformedFrom(NettyMessageProcessorFactory.class, "udp");
    }

    /** Netty TCP, From without '>': the connection stays open */
    @Test(timeout = 30000)
    public void testNettyTcpMalformedFrom() throws Exception {
        checkMalformedFrom(NettyMessageProcessorFactory.class, "tcp");
    }

    private void checkMalformedFrom(Class<?> transport, String protocol) throws Exception {
        shootme = new Shootme(shootmePort, transport.getName(), protocol);
        shootist = new Shootist(shootistPort, shootmePort, protocol);
        shootist.sendInviteWithMalformedFrom();
        AssertUntil.assertUntil(shootist.getFinalInviteResponseAssertion(), TIMEOUT);

        // RFC 3261 21.4.1: the stack can't take a request with a broken mandatory header, answers 400 itself
        List<MessageRecord> finals = shootist.getFinalInviteResponses();
        Assert.assertFalse("No response arrived at the sender", finals.isEmpty());
        MessageRecord response = finals.get(0);
        Assert.assertEquals("Sender got " + response.status + " " + response.reasonPhrase + " instead of 400 Bad Request",
                Response.BAD_REQUEST, response.status);
        Assert.assertTrue("Application got the request", shootme.getReceivedInvites().isEmpty());
        if (protocol.equals("tcp")) {
            Assert.assertFalse("Connection closed after the 400", shootist.isConnectionClosed());
        }
    }

    /** Netty UDP proxy */
    @Test(timeout = 30000)
    public void testNettyProxyForwardsMalformedHeaders() throws Exception {
        checkProxyForwards(NettyMessageProcessorFactory.class, "udp");
    }

    /** Netty TCP proxy */
    @Test(timeout = 30000)
    public void testNettyTcpProxyForwardsMalformedHeaders() throws Exception {
        checkProxyForwards(NettyMessageProcessorFactory.class, "tcp");
    }

    //shootist -> proxy -> shootme, the proxy runs on the path under test, shootme on Netty
    private void checkProxyForwards(Class<?> transport, String protocol) throws Exception {
        proxy = new Proxy(proxyPort, shootmePort, transport.getName(), protocol);
        shootme = new Shootme(shootmePort, NettyMessageProcessorFactory.class.getName(), protocol);
        shootme.setMalformedResponseHeader(Shootist.MALFORMED_P_ASSERTED_IDENTITY);
        shootist = new Shootist(shootistPort, proxyPort, protocol);
        shootist.sendInvite(Shootist.MALFORMED_P_PREFERRED_IDENTITY);
        AssertUntil.assertUntil(shootist.getFinalInviteResponseAssertion(), TIMEOUT);

        // RFC 3261 16.6: the proxy sends the request on with the malformed header as is
        List<MessageRecord> invites = shootme.getReceivedInvites();
        Assert.assertFalse("Proxy sent no INVITE on", invites.isEmpty());
        Assert.assertTrue("Malformed header dropped by the proxy on the request",
                invites.get(0).unparsedHeaders.contains(Shootist.MALFORMED_P_PREFERRED_IDENTITY));

        // RFC 3261 16.7: the response goes back with the malformed header as is
        Assert.assertFalse("No response arrived at the sender", shootist.getFinalInviteResponses().isEmpty());
        Assert.assertTrue("Malformed header dropped by the proxy on the response",
                shootist.receivedLine(Shootist.MALFORMED_P_ASSERTED_IDENTITY));
    }

    /** OPTIONS with one extra line in place of %s, Content-Length 0 */
    private static final String OPTIONS_WITH_HEADER = "OPTIONS sip:bob@127.0.0.1 SIP/2.0\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-malformed-1\r\n"
            + "Max-Forwards: 70\r\n"
            + "From: <sip:alice@127.0.0.1>;tag=1234\r\n"
            + "To: <sip:bob@127.0.0.1>\r\n"
            + "Call-ID: malformed-1@127.0.0.1\r\n"
            + "CSeq: 1 OPTIONS\r\n"
            + "%s\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";

    /** 200 OK with one extra line in place of %s, Content-Length 0 */
    private static final String OK_WITH_HEADER = "SIP/2.0 200 OK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-malformed-1\r\n"
            + "From: <sip:alice@127.0.0.1>;tag=1234\r\n"
            + "To: <sip:bob@127.0.0.1>;tag=4321\r\n"
            + "Call-ID: malformed-1@127.0.0.1\r\n"
            + "CSeq: 1 OPTIONS\r\n"
            + "%s\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";

    private static String options(String extraHeader) {
        return String.format(OPTIONS_WITH_HEADER, extraHeader);
    }

    //From without '>'
    private static String optionsWithBrokenFrom() {
        return options("Allow: OPTIONS").replace("From: <sip:alice@127.0.0.1>;tag=1234",
                "From: <sip:alice@127.0.0.1;tag=1234");
    }

    private static String optionsWithBrokenCSeq() {
        return options("Allow: OPTIONS").replace("CSeq: 1 OPTIONS", "CSeq: x OPTIONS");
    }

    private static boolean hasUnrecognized(SIPMessage msg, String header) {
        ListIterator<String> it = msg.getUnrecognizedHeaders();
        while (it.hasNext()) {
            if (it.next().equals(header)) {
                return true;
            }
        }
        return false;
    }

    private static SIPMessage parseNetty(String message) throws ParseException {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        Assert.assertTrue(parser.parseBytes(Unpooled.wrappedBuffer(message.getBytes())).isParsingComplete());
        return parser.consumeSIPMessage();
    }

    //returns what the parser keeps for the 400
    private static SIPMessage parseNettyMalformed(String message) {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        Assert.assertTrue(parser.parseBytes(Unpooled.wrappedBuffer(message.getBytes())).isParsingComplete());
        try {
            parser.consumeSIPMessage();
            Assert.fail("expected ParseException");
        } catch (ParseException e) {
            // expected
        }
        Assert.assertNotNull(parser.getMalformedMessage());
        return parser.getMalformedMessage();
    }

    /** Netty, Expires: abc in a request: kept as unrecognized, the request goes on */
    @Test
    public void testNettyParserMalformedOptionalHeaderInRequest() throws Exception {
        SIPMessage msg = parseNetty(options("Expires: abc"));
        Assert.assertNotNull(msg);
        Assert.assertNull(msg.getHeader(ExpiresHeader.NAME));
        Assert.assertTrue(hasUnrecognized(msg, "Expires: abc"));
    }

    /** Netty, Expires: abc in a response: kept as unrecognized, the response goes on */
    @Test
    public void testNettyParserMalformedOptionalHeaderInResponse() throws Exception {
        SIPMessage msg = parseNetty(String.format(OK_WITH_HEADER, "Expires: abc"));
        Assert.assertTrue(msg instanceof SIPResponse);
        Assert.assertTrue(hasUnrecognized(msg, "Expires: abc"));
    }

    /** Netty, From without '>': rejected, the request kept for the 400 has everything but From */
    @Test
    public void testNettyParserMalformedMandatoryHeaderInRequest() throws Exception {
        SIPMessage malformed = parseNettyMalformed(optionsWithBrokenFrom());
        Assert.assertTrue(malformed instanceof SIPRequest);
        Assert.assertNull(malformed.getFrom());
        Assert.assertEquals("malformed-1@127.0.0.1", malformed.getCallId().getCallId());
    }

    /** Netty, CSeq: x in a response: rejected, it is a response so the decoder sends no 400 */
    @Test
    public void testNettyParserMalformedMandatoryHeaderInResponse() throws Exception {
        String message = String.format(OK_WITH_HEADER, "Allow: OPTIONS").replace("CSeq: 1 OPTIONS", "CSeq: x OPTIONS");
        SIPMessage malformed = parseNettyMalformed(message);
        Assert.assertTrue(malformed instanceof SIPResponse);
        Assert.assertNull(malformed.getCSeq());
    }

    /** Netty, broken message then a good one on the same stream: first rejected, second parsed */
    @Test
    public void testNettyParserRecoversAfterMalformedMandatoryHeader() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer((optionsWithBrokenCSeq() + options("Allow: OPTIONS")).getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());
        try {
            parser.consumeSIPMessage();
            Assert.fail("expected ParseException");
        } catch (ParseException e) {
            Assert.assertNull(parser.getMalformedMessage().getCSeq());
        }
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());
        SIPMessage good = parser.consumeSIPMessage();
        Assert.assertNotNull(good);
        Assert.assertEquals(1, good.getCSeq().getSeqNumber());
        Assert.assertNull(parser.getMalformedMessage());
    }
}
