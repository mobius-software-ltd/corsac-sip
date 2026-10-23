package test.unit.gov.nist.javax.sip.stack.malformed;

import java.nio.charset.StandardCharsets;

import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.StringMsgParser;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * A malformed optional header kept in the unrecognized headers is sent on
 * when the message is encoded for the wire (forwarding)
 */
public class UnrecognizedHeadersEncodingTest extends TestCase {

    private static final String OK_WITH_BROKEN_EXPIRES = "SIP/2.0 200 OK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-malformed-1\r\n"
            + "From: <sip:alice@127.0.0.1>;tag=1234\r\n"
            + "To: <sip:bob@127.0.0.1>;tag=4321\r\n"
            + "Call-ID: malformed-1@127.0.0.1\r\n"
            + "CSeq: 1 OPTIONS\r\n"
            + "Expires: abc\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";

    private static SIPMessage parse(String message) throws Exception {
        return new StringMsgParser().parseSIPMessage(message.getBytes(StandardCharsets.UTF_8), true, false,
                new MalformedHeaderTest.ChannelLikeListener());
    }

    private static String headers(byte[] encoded) {
        String text = new String(encoded, StandardCharsets.UTF_8);
        return text.substring(0, text.indexOf("\r\n\r\n") + 2);
    }

    /** request with Expires: abc: encodeAsBytes keeps it, the next hop sees it as unrecognized again */
    public void testRequestKeepsUnrecognizedHeader() throws Exception {
        SIPMessage request = parse(MalformedHeaderTest.options("Expires: abc"));
        byte[] encoded = request.encodeAsBytes("TCP");
        Assert.assertTrue(headers(encoded).contains("\r\nExpires: abc\r\n"));
        SIPMessage reparsed = parse(new String(encoded, StandardCharsets.UTF_8));
        Assert.assertTrue(MalformedHeaderTest.hasUnrecognized(reparsed, "Expires: abc"));
    }

    /** response with Expires: abc: encodeAsBytes keeps it */
    public void testResponseKeepsUnrecognizedHeader() throws Exception {
        SIPMessage response = parse(OK_WITH_BROKEN_EXPIRES);
        Assert.assertTrue(response instanceof SIPResponse);
        Assert.assertTrue(headers(response.encodeAsBytes("TCP")).contains("\r\nExpires: abc\r\n"));
    }

    /** clone of a request with Expires: abc, as a proxy forwards it: encodeAsBytes keeps it */
    public void testForwardedCloneKeepsUnrecognizedHeader() throws Exception {
        SIPRequest request = (SIPRequest) parse(MalformedHeaderTest.options("Expires: abc"));
        SIPRequest forwarded = (SIPRequest) request.clone();
        Assert.assertTrue(headers(forwarded.encodeAsBytes("UDP")).contains("\r\nExpires: abc\r\n"));
    }
}
