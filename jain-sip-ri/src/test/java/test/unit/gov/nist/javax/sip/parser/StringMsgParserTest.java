package test.unit.gov.nist.javax.sip.parser;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.StringMsgParser;
import junit.framework.Assert;
import org.junit.Test;

public class StringMsgParserTest extends junit.framework.TestCase {

    public StringMsgParserTest() {
    }

    private static final String IPV6MESSAGE = "INVITE sip:12345@[2001:db8::120] SIP/2.0\r\n"
            + "Call-ID: c7e5af7ba19670f4567d410e48b70716@2001:db8:0:0:0:0:0:130%4\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "From: <sip:outcall@[2001:db8::130]>;tag=36542626_71cae4f3_a1863e0e_3b8c63fe\r\n"
            + "To: <sip:12345@[2001:db8::120]>\r\n"
            + "Max-Forwards: 69\r\n"
            + "Contact: <sip:outcall@[2001:db8:0:0:0:0:0:130]:5060;transport=tcp>\r\n"
            + "Via: SIP/2.0/TCP [fe80::20c:29ff:febc:37c7]:5065;branch=z9hG4bK3b8c63fe_a1863e0e_fcc83f35-63ff-4af8-9596-475022f803f7c7e5azsd_0,SIP/2.0/TCP [2001:db8::120]:5060;branch=z9hG4bK3b8c63fe_a1863e0e_fcc83f35-63ff-4af8-9596-475022f803f7c7e5a_0,SIP/2.0/TCP [2001:db8:0:0:0:0:0:130]:5060;branch=z9hG4bK3b8c63fe_a1863e0e_fcc83f35-63ff-4af8-9596-475022f803f7;received=2001:db8:0:0:0:0:0:130;rport=60154\r\n"
            + "Content-Type: application/sdp\r\n"
            + "X-Sip-Balancer-InitialRemoteAddr: 2001:db8:0:0:0:0:0:130\r\n"
            + "X-Sip-Balancer-InitialRemotePort: 60154\r\n"
            + "Route: <sip:[fe80:0:0:0:20c:29ff:fe7d:7f9c]:5060;transport=tcp;lr>\r\n"
            + "Record-Route: <sip:[fe80:0:0:0:20c:29ff:febc:37c7]:5065;transport=tcp;lr;node_host=fe80:0:0:0:20c:29ff:fe7d:7f9c%252;node_port=5060;version=0>,<sip:[2001:db8:0:0:0:0:0:120]:5060;transport=tcp;lr;node_host=fe80:0:0:0:20c:29ff:fe7d:7f9c%252;node_port=5060;version=0>\r\n"
            + "Content-Length: 132\r\n\r\n"
            + "v=0\r\n"
            + "o=- 0 0 IN IP4 192.168.100.70\r\n"
            + "s=-\r\n"
            + "c=IN IP4 192.168.100.70\r\n"
            + "t=0 0\r\n"
            + "m=audio 39072 RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=ptime:20\r\n";

    public void testIPV6ScopeIdParam() throws Exception {
        StringMsgParser parser = new StringMsgParser();
        SIPMessage msg = parser.parseSIPMessage((IPV6MESSAGE).getBytes(), true, false, null);
        RecordRoute routeHdr = (RecordRoute) msg.getHeader("Record-Route");
        SipUri uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));
    }

}
