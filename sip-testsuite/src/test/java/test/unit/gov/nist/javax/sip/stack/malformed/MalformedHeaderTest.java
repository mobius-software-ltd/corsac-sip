package test.unit.gov.nist.javax.sip.stack.malformed;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sip.SipFactory;
import javax.sip.SipStack;
import javax.sip.header.ExpiresHeader;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.header.Expires;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.header.StatusLine;
import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.NettyMessageParser;
import gov.nist.javax.sip.parser.NioPipelineParser;
import gov.nist.javax.sip.parser.Pipeline;
import gov.nist.javax.sip.parser.PipelinedMsgParser;
import gov.nist.javax.sip.parser.SIPMessageListener;
import gov.nist.javax.sip.parser.StringMsgParser;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.transports.processors.netty.NettyMessageProcessorFactory;
import gov.nist.javax.sip.stack.transports.processors.nio.NioMessageProcessorFactory;
import gov.nist.javax.sip.stack.transports.processors.oio.OIOMessageProcessorFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.unit.gov.nist.javax.sip.stack.malformed.Records.MessageRecord;

/**
 * Malformed P-Preferred-Identity (no closing '>'): per RFC 3261 the stack ignores it and
 * passes the INVITE on, keeps the header for forwarding, leaves answering to the app
 * <p>
 * The tests after the two flows feed each parser directly, no network
 */
public class MalformedHeaderTest {

    private static final int TIMEOUT = 5000;

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

    private static final long PROCESS_WAIT = 2000;

    //each parser test gets its own stack, SipFactory hands back an existing one with the same name
    private static final AtomicInteger STACK_COUNT = new AtomicInteger();

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

    //also used by UnrecognizedHeadersEncodingTest
    static String options(String extraHeader) {
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

    static boolean hasUnrecognized(SIPMessage msg, String header) {
        ListIterator<String> it = msg.getUnrecognizedHeaders();
        while (it.hasNext()) {
            if (it.next().equals(header)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stands in for the transport channel when a parser is fed directly. Same rule as the
     * channels' handleException: a broken mandatory header rejects the message, any other
     * goes to the unrecognized headers. Records what the parser passed on and reported
     */
    static class ChannelLikeListener implements SIPMessageListener {

        private final BlockingQueue<SIPMessage> processed = new LinkedBlockingQueue<SIPMessage>();
        private final List<Class<?>> reportedHeaderClasses = new CopyOnWriteArrayList<Class<?>>();

        @Override
        public void processMessage(SIPMessage msg) throws Exception {
            processed.add(msg);
        }

        @Override
        public void sendSingleCLRF() throws Exception {
        }

        @Override
        public void handleException(ParseException ex, SIPMessage sipMessage, Class<?> hdrClass, String header,
                String message) throws ParseException {
            reportedHeaderClasses.add(hdrClass);
            if (isMandatory(hdrClass)) {
                throw ex;
            }
            sipMessage.addUnparsed(header);
        }

        //the headers the channels treat as mandatory in handleException
        private static boolean isMandatory(Class<?> hdrClass) {
            return hdrClass != null && (hdrClass.equals(From.class) || hdrClass.equals(To.class)
                    || hdrClass.equals(CSeq.class) || hdrClass.equals(Via.class) || hdrClass.equals(CallID.class)
                    || hdrClass.equals(ContentLength.class) || hdrClass.equals(RequestLine.class)
                    || hdrClass.equals(StatusLine.class));
        }

        //everything passed on until nothing more comes within PROCESS_WAIT
        List<SIPMessage> drainProcessed() throws InterruptedException {
            List<SIPMessage> messages = new ArrayList<SIPMessage>();
            SIPMessage msg = processed.poll(PROCESS_WAIT, TimeUnit.MILLISECONDS);
            while (msg != null) {
                messages.add(msg);
                msg = processed.poll(PROCESS_WAIT, TimeUnit.MILLISECONDS);
            }
            return messages;
        }

        List<Class<?>> getReportedHeaderClasses() {
            return reportedHeaderClasses;
        }
    }

    private static SipStack createStack(Class<?> messageProcessorFactory) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "malformed-" + STACK_COUNT.incrementAndGet());
        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", messageProcessorFactory.getName());
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        return sipFactory.createSipStack(properties);
    }

    //NIO TCP parser, returns what it passed on to the channel
    private static List<SIPMessage> parseNio(String messages, ChannelLikeListener channel) throws Exception {
        SipStack stack = createStack(NioMessageProcessorFactory.class);
        NioPipelineParser parser = new NioPipelineParser((SIPTransactionStack) stack, channel, 10000);
        try {
            parser.addBytes(messages.getBytes());
            return channel.drainProcessed();
        } finally {
            parser.close();
            stack.stop();
        }
    }

    //OIO TCP parser, returns what it passed on to the channel
    private static List<SIPMessage> parseOio(String messages, ChannelLikeListener channel) throws Exception {
        SipStack stack = createStack(OIOMessageProcessorFactory.class);
        SIPTransactionStack transactionStack = (SIPTransactionStack) stack;
        //a channel passes the socket's input stream here, Pipeline.close() closes it so it can't be null
        Pipeline pipeline = new Pipeline(new ByteArrayInputStream(new byte[0]), -1, transactionStack.getTimer());
        try {
            new PipelinedMsgParser(transactionStack, channel, pipeline, 10000).processInput();
            pipeline.write(messages.getBytes());
            return channel.drainProcessed();
        } finally {
            pipeline.close();
            stack.stop();
        }
    }

    //UDP parser (NIO and OIO)
    private static SIPMessage parseUdp(String message, ChannelLikeListener channel) throws ParseException {
        return new StringMsgParser().parseSIPMessage(message.getBytes(StandardCharsets.UTF_8), true, false, channel);
    }

    private static SIPMessage parseNetty(String message) throws ParseException {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        Assert.assertTrue(parser.parseBytes(Unpooled.wrappedBuffer(message.getBytes())).isParsingComplete());
        return parser.consumeSIPMessage();
    }

    //parses a message that must be rejected, returns what the Netty parser keeps for the 400
    private static String parseNettyMalformed(String message) {
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

    /** Netty, From without '>': rejected, the text for the 400 still has the broken line */
    @Test
    public void testNettyParserMalformedMandatoryHeaderInRequest() throws Exception {
        String raw = parseNettyMalformed(optionsWithBrokenFrom());
        Assert.assertTrue(raw.startsWith("OPTIONS sip:bob@127.0.0.1 SIP/2.0\r\n"));
        Assert.assertTrue(raw.contains("From: <sip:alice@127.0.0.1;tag=1234\r\n"));
    }

    /** Netty, CSeq: x in a response: rejected, the text starts with SIP/ so the decoder sends no 400 */
    @Test
    public void testNettyParserMalformedMandatoryHeaderInResponse() throws Exception {
        String message = String.format(OK_WITH_HEADER, "Allow: OPTIONS").replace("CSeq: 1 OPTIONS", "CSeq: x OPTIONS");
        String raw = parseNettyMalformed(message);
        Assert.assertTrue(raw.startsWith("SIP/2.0 200 OK"));
        Assert.assertTrue(raw.contains("CSeq: x OPTIONS\r\n"));
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
            Assert.assertTrue(parser.getMalformedMessage().contains("CSeq: x OPTIONS"));
            Assert.assertFalse(parser.getMalformedMessage().contains("CSeq: 1 OPTIONS"));
        }
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());
        Assert.assertNotNull(parser.consumeSIPMessage());
        Assert.assertNull(parser.getMalformedMessage());
    }

    /** UDP, Expires: abc: reported to the channel, kept as unrecognized, the message goes on */
    @Test
    public void testUdpParserMalformedOptionalHeader() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        SIPMessage msg = parseUdp(options("Expires: abc"), channel);
        Assert.assertNotNull(msg);
        Assert.assertNull(msg.getHeader(ExpiresHeader.NAME));
        Assert.assertTrue(hasUnrecognized(msg, "Expires: abc"));
        Assert.assertEquals(Expires.class, channel.getReportedHeaderClasses().get(0));
    }

    /** UDP, From without '>': reported to the channel, the message is rejected */
    @Test
    public void testUdpParserMalformedMandatoryHeader() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        try {
            parseUdp(optionsWithBrokenFrom(), channel);
            Assert.fail("expected ParseException");
        } catch (ParseException e) {
            // expected
        }
        Assert.assertEquals(From.class, channel.getReportedHeaderClasses().get(0));
    }

    /** NIO TCP, Expires: abc: reported to the channel, kept as unrecognized, the message goes on */
    @Test
    public void testNioParserMalformedOptionalHeader() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        List<SIPMessage> messages = parseNio(options("Expires: abc"), channel);
        Assert.assertEquals(1, messages.size());
        Assert.assertTrue(hasUnrecognized(messages.get(0), "Expires: abc"));
        Assert.assertEquals(Expires.class, channel.getReportedHeaderClasses().get(0));
    }

    /** NIO TCP, From without '>': reported to the channel, the message is rejected */
    @Test
    public void testNioParserMalformedMandatoryHeader() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        Assert.assertTrue(parseNio(optionsWithBrokenFrom(), channel).isEmpty());
        Assert.assertEquals(From.class, channel.getReportedHeaderClasses().get(0));
    }

    /** OIO TCP, Expires: abc: reported to the channel, kept as unrecognized, the message goes on */
    @Test
    public void testOioParserMalformedOptionalHeader() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        List<SIPMessage> messages = parseOio(options("Expires: abc"), channel);
        Assert.assertEquals(1, messages.size());
        Assert.assertTrue(hasUnrecognized(messages.get(0), "Expires: abc"));
        Assert.assertEquals(Expires.class, channel.getReportedHeaderClasses().get(0));
    }

    /** OIO TCP, From without '>': reported to the channel, the message is rejected */
    @Test
    public void testOioParserMalformedMandatoryHeader() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        Assert.assertTrue(parseOio(optionsWithBrokenFrom(), channel).isEmpty());
        Assert.assertEquals(From.class, channel.getReportedHeaderClasses().get(0));
    }

    /** OIO TCP, rejected message then a good one on the same stream: only the good one is passed on */
    @Test
    public void testOioParserNextMessageAfterRejectedOne() throws Exception {
        ChannelLikeListener channel = new ChannelLikeListener();
        List<SIPMessage> messages = parseOio(optionsWithBrokenFrom() + options("Allow: OPTIONS"), channel);
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals("1234", messages.get(0).getFrom().getTag());
    }
}
