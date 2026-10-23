package test.unit.gov.nist.javax.sip.stack.malformed;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.StringMsgParser;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * The 400 the TCP and Netty paths send for a request with a malformed mandatory header
 */
public class BadRequestResponseTest extends TestCase {

    private static final String BAD_REQUEST = "OPTIONS sip:bob@127.0.0.1 SIP/2.0\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-badreq-1\r\n"
            + "Max-Forwards: 70\r\n"
            + "From: \"Alice\" <sip:alice@127.0.0.1>;tag=1234\r\n"
            + "To: <sip:bob@127.0.0.1>\r\n"
            + "Call-ID: badreq-1@127.0.0.1\r\n"
            + "CSeq: x OPTIONS\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";

    private static SIPResponse parse(String response) throws ParseException {
        return (SIPResponse) new StringMsgParser().parseSIPMessage(
                response.getBytes(StandardCharsets.UTF_8), true, false, null);
    }

    /** TCP: the 400 parses, Via, Call-ID and From are copied, To gets a tag, the request is the body */
    public void testResponseIsWellFormed() throws Exception {
        String response = MessageChannel.createBadRequestResponse(BAD_REQUEST, new ParseException("bad CSeq", 0), false);
        Assert.assertNotNull(response);
        SIPResponse parsed = parse(response);
        Assert.assertEquals(400, parsed.getStatusCode());
        Assert.assertEquals("z9hG4bK-badreq-1", parsed.getTopmostVia().getBranch());
        Assert.assertEquals("badreq-1@127.0.0.1", parsed.getCallId().getCallId());
        Assert.assertEquals("1234", parsed.getFrom().getTag());
        Assert.assertEquals("badreq", parsed.getTo().getTag());
        Assert.assertEquals("message/sipfrag", parsed.getContentTypeHeader().getContentType()
                + "/" + parsed.getContentTypeHeader().getContentSubType());
        Assert.assertEquals(BAD_REQUEST, new String(parsed.getRawContent(), StandardCharsets.UTF_8));
    }

    /** UDP: the 400 parses */
    public void testDatagramResponseIsWellFormed() throws Exception {
        String response = MessageChannel.createBadRequestResponse(BAD_REQUEST, new ParseException("bad CSeq", 0), true);
        Assert.assertNotNull(response);
        Assert.assertEquals(400, parse(response).getStatusCode());
    }

    /** no Via to copy: no 400 */
    public void testNoResponseWithoutMandatoryHeaders() throws Exception {
        String noVia = BAD_REQUEST.replace("Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-badreq-1\r\n", "");
        Assert.assertNull(MessageChannel.createBadRequestResponse(noVia, new ParseException("no Via", 0), false));
    }
}
