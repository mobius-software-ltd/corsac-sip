/*
 * Mobius Software LTD
 * Copyright 2023, Mobius Software LTD and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package gov.nist.javax.sip.parser;

import java.text.ParseException;

import javax.sip.header.AllowHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.ViaHeader;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import junit.framework.Assert;

public class NettyMessageParserTest extends junit.framework.TestCase {

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
    private static final String CRLF = "\r\n";
    private static final String DOUBLE_CRLF = "\r\n\r\n";
    
    private static final String HEADER_CHUNK = "INVITE sip:00001002000022@p25dr;user=TIA-P25-SU SIP/2.0\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "From: <sip:0000100200000c@p25dr;user=TIA-P25-SU>;tag=841\r\n"
            + "To: <sip:00001002000022@p25dr;user=TIA-P25-SU>\r\n"
            + "Via: SIP/2.0/UDP 02.002.00001.p25dr;branch=z9hG4bKa10f04383e3d8e8dbf3f6d06f6bb6880\r\n"
            + "Max-Forwards: 70\r\n"
            + "Contact: <sip:02.002.00001.p25dr>\r\n"
            + "Call-ID: c6a12ddad0ddc1946d9f443c884a7768@127.0.0.1\r\n"
            + "Content-Type: application/sdp;level=1\r\n"
            + "Content-Length: 145\r\n";
    private static final String EMPTY_MESSAGE_BODY = "REGISTER sip:00001002000022@p25dr;user=TIA-P25-SU SIP/2.0\r\n"
            + "CSeq: 1 REGISTER\r\n"
            + "From: <sip:0000100200000c@p25dr;user=TIA-P25-SU>;tag=841\r\n"
            + "To: <sip:00001002000022@p25dr;user=TIA-P25-SU>\r\n"
            + "Via: SIP/2.0/UDP 02.002.00001.p25dr;branch=z9hG4bKa10f04383e3d8e8dbf3f6d06f6bb6880\r\n"
            + "Max-Forwards: 70\r\n"
            + "Contact: <sip:02.002.00001.p25dr>\r\n"
            + "Call-ID: c6a12ddad0ddc1946d9f443c884a7768@127.0.0.1\r\n"
            + "Expires: 0\r\n"
            + "Content-Type: application/sdp;level=1\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";
    private static final String HEADER_CHUNK_COMPACT = "INVITE sip:00001002000022@p25dr;user=TIA-P25-SU SIP/2.0\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "f: <sip:0000100200000c@p25dr;user=TIA-P25-SU>;tag=841\r\n"
            + "t: <sip:00001002000022@p25dr;user=TIA-P25-SU>\r\n"
            + "v: SIP/2.0/UDP 02.002.00001.p25dr;branch=z9hG4bKa10f04383e3d8e8dbf3f6d06f6bb6880\r\n"
            + "Max-Forwards: 70\r\n"
            + "m: <sip:02.002.00001.p25dr>\r\n"
            + "i: c6a12ddad0ddc1946d9f443c884a7768@127.0.0.1\r\n"
            + "c: application/sdp;level=1\r\n"
            + "l: 145\r\n";

    private static final String INVITE_HEADER_CHUNK = "INVITE sip:00001002000022@p25dr;user=TIA-P25-SU SIP/2.0\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "From: <sip:0000100200000c@p25dr;user=TIA-P25-SU>;tag=841\r\n"
            + "To: <sip:00001002000022@p25dr;user=TIA-P25-SU>\r\n"
            + "Via: SIP/2.0/UDP 02.002.00001.p25dr;branch=z9hG4bKa10f04383e3d8e8dbf3f6d06f6bb6880\r\n"
            + "Max-Forwards: 70\r\n"
            + "Contact: <sip:02.002.00001.p25dr>\r\n"
            + "Call-ID: c6a12ddad0ddc1946d9f443c884a7768@127.0.0.1\r\n"
            + "Content-Type: application/sdp;level=1\r\n";
    private static final String HEADER1 = "Allow: REGISTER,INVITE,ACK,BYE,CANCEL\r";
    private static final String HEADER2 = "\n";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length: 145\r\n";

    private static final String BODY_CHUNK = 
            "v=0\r\n"
            + "o=- 30576 0 IN IP4 127.0.0.1\r\n"
            + "s=TIA-P25-SuToSuCall\r\n"
            + "t=0 0\r\n"
            + "c=IN IP4 127.0.0.1\r\n"
            + "m=audio 12412 RTP/AVP 100\r\n"
            + "a=rtpmap:100 X-TIA-P25-IMBE/8000\r\n";

    private static final String TRYING_RESPONSE = "SIP/2.0 100 Trying\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: eeabbcdfd26e27152ae84f822bc5ec80@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>\r\n"
            + "Via: SIP/2.0/UDP 127.0.0.1:57939;branch=z9hG4bK-333437-c046d25f4ed4c4228324c5d6d5320781\r\n"
            + "Contact: <sip:127.0.0.1:57937;transport=udp>\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";
    
    private static final String OK_RESPONSE = "SIP/2.0 200 OK\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: bfca4ec24f6fb289a2ce4c244edbc248@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:57196;branch=z9hG4bK-35-013673b6085013b28485731f1cf04b89;rport=59686\r\n"
            + "Contact: \"Shootme\" <sip:127.0.0.1:5070>\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";

    private static final String SPLIT_OK_RESPONSE_FIRST_PART = "SIP/2.0 200 OK\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: bfca4ec24f6fb289a2ce4c244edbc248@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\"";
            
    private static final String SPLIT_OK_RESPONSE_SECOND_PART =
              "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:57196;branch=z9hG4bK-35-013673b6085013b28485731f1cf04b89;rport=59686\r\n"
            + "Contact: \"Shootme\" <sip:127.0.0.1:5070>\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";

    private static final String SPLIT_BODY_CHUNK_FIRST_PART = 
            "v=0\r\n"
            + "o=- 30576 0 IN IP4 127.0.0.1\r\n"
            + "s=TIA-P25-SuTo";    

    private static final String SPLIT_BODY_CHUNK_SECOND_PART = 
            "SuCall\r\n"
            + "t=0 0\r\n"
            + "c=IN IP4 127.0.0.1\r\n"
            + "m=audio 12412 RTP/AVP 100\r\n"
            + "a=rtpmap:100 X-TIA-P25-IMBE/8000\r\n";    

    private static final String MULTIPLE_RESPONSES = "SIP/2.0 180 Ringing\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: 1294dff2c0a3bc9ac9be8801988193cd@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56057;branch=z9hG4bK-3838-f719e29aa8a841ec204a8984f6ab42c8;rport=34784\r\n"
            + "Contact: \"Shootme\" <sip:127.0.0.1:5070>\r\n"
            + "Content-Length: 0\r\n"            
            + "\r\n"
            + "SIP/2.0 200 OK\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: 1294dff2c0a3bc9ac9be8801988193cd@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56057;branch=z9hG4bK-3838-f719e29aa8a841ec204a8984f6ab42c8;rport=34784\r\n"
            + "Contact: \"Shootme\" <sip:127.0.0.1:5070>\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";            
            
     private static final String MULTIPLE_ACK_REQUESTS_SPLIT_FIRST_PART = "ACK sip:127.0.0.1:5070 SIP/2.0\r\n"
            + "Call-ID: dcf2dbba51cbf75e8f2489862cde78a1@127.0.0.1\r\n"
            + "CSeq: 1 ACK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56032;branch=z9hG4bK-3232-1d60ecf75c3a9f105ea186c105e07363\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Max-Forwards: 70\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n"
            + "ACK sip:127.0.0.1:5070 SIP/2.0\r\n"
            + "Call-ID: dcf2dbba51cbf75e8f2489862cde78a1@127.0.0.1\r\n"
            + "CSeq: 2 ACK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56032;branch=z9hG4bK-3232-1d60ecf75c3a9f105ea186c105e07363\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Max-Forwards: 70\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n"
            + "ACK sip:127.0.0.1:5070 SIP/2.0\r\n"
            + "Call-ID: dcf2dbba51cbf75e8f2489862cde78a1@127.0.0.1\r\n"
            + "CSeq: 3 ACK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56032;branch=z9hG4bK-3232-1d60ecf75c3a9f105ea186c105e07363\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Max-Forwards: 70\r\n"
            + "Content-Le"; 
            
    private static final String MULTIPLE_ACK_REQUESTS_SPLIT_SECOND_PART = 
              "Content-Length: 0\r\n"
            + "\r\n"
            + "ACK sip:127.0.0.1:5070 SIP/2.0\r\n"
            + "Call-ID: dcf2dbba51cbf75e8f2489862cde78a1@127.0.0.1\r\n"
            + "CSeq: 4 ACK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56032;branch=z9hG4bK-3232-1d60ecf75c3a9f105ea186c105e07363\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Max-Forwards: 70\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n"; 

    private static final String MULTIPLE_ACK_REQUESTS_SPLIT_THIRD_PART =               
            "ACK sip:127.0.0.1:5070 SIP/2.0"; 

    private static final String MULTIPLE_ACK_REQUESTS_SPLIT_FOURTH_PART =               
            "\r\n"
            + "Call-ID: dcf2dbba51cbf75e8f2489862cde78a1@127.0.0.1\r\n"
            + "CSeq: 5 ACK\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56032;branch=z9hG4bK-3232-1d60ecf75c3a9f105ea186c105e07363\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Max-Forwards: 70\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n"; 
        
    private static final String MULTIPLE_RESPONSES_PARSE_EXCEPTION = "SIP/2.0 180 Ringing\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: 1294dff2c0a3bc9ac9be8801988193cd@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Via: SIP/2.0TCP 127.0.0.1:56057;branch=z9hG4bK-3838-f719e29aa8a841ec204a8984f6ab42c8;rport=34784\r\n"
            + "Contact: \"Shootme\" <sip:127.0.0.1:5070>\r\n"
            + "Content-Length: 0\r\n"            
            + "\r\n"
            + "SIP/2.0 200 OK\r\n"
            + "CSeq: 1 INVITE\r\n"
            + "Call-ID: 1294dff2c0a3bc9ac9be8801988193cd@127.0.0.1\r\n"
            + "From: \"The Master Blaster\" <sip:BigGuy@here.com>;tag=12345\r\n"
            + "To: \"The Little Blister\" <sip:LittleGuy@there.com>;tag=4321\r\n"
            + "Via: SIP/2.0/TCP 127.0.0.1:56057;branch=z9hG4bK-3838-f719e29aa8a841ec204a8984f6ab42c8;rport=34784\r\n"
            + "Contact: \"Shootme\" <sip:127.0.0.1:5070>\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";    

    public void testIPV6ScopeIdParam() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(IPV6MESSAGE.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();
        RecordRoute routeHdr = (RecordRoute) msg.getHeader("Record-Route");
        SipUri uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));
    }

    public void testTryingResponse() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(TRYING_RESPONSE.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();  
        ContactHeader contact = (ContactHeader) msg.getHeader("Contact");
        SipUri uri = (SipUri) contact.getAddress().getURI();
        Assert.assertEquals("udp", uri.getTransportParam());
    }

    public void testOKResponse() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(OK_RESPONSE.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();       
        ViaHeader via = (ViaHeader) msg.getHeader("Via");        
        Assert.assertEquals("TCP", via.getTransport());
    }

    public void testSplitOKResponse() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(SPLIT_OK_RESPONSE_FIRST_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();       
        assertNull(msg);
        ByteBuf byteBuf2 = Unpooled.wrappedBuffer(SPLIT_OK_RESPONSE_SECOND_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf2).isParsingComplete());        
        msg = parser.consumeSIPMessage();
        assertNotNull(msg);
        ViaHeader via = (ViaHeader) msg.getHeader("Via");        
        Assert.assertEquals("TCP", via.getTransport());
    }


    public void testEmptyMessageBody() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(EMPTY_MESSAGE_BODY.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();               
        ExpiresHeader expiresHeader = (ExpiresHeader) msg.getHeader("Expires");
        int expires = expiresHeader.getExpires();
        Assert.assertEquals(0, expires);
        assertNull(msg.getContent());
    }

    public void testMultipleRequests() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(IPV6MESSAGE.getBytes());
        ByteBuf secondByteBuf = Unpooled.wrappedBuffer(EMPTY_MESSAGE_BODY.getBytes());
        ByteBuf thirdBuf = Unpooled.wrappedBuffer(IPV6MESSAGE.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(byteBuf, secondByteBuf, thirdBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();               
        RecordRoute routeHdr = (RecordRoute) msg.getHeader("Record-Route");
        SipUri uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));
        assertNotNull(msg.getContent());
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg2 = parser.consumeSIPMessage();               
        ExpiresHeader expiresHeader = (ExpiresHeader) msg2.getHeader("Expires");
        int expires = expiresHeader.getExpires();
        Assert.assertEquals(0, expires);
        assertNull(msg2.getContent());       
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg3 = parser.consumeSIPMessage();               
        routeHdr = (RecordRoute) msg3.getHeader("Record-Route");
        uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));
        assertNotNull(msg3.getContent());        
    }

    public void testMultipleACKRequestsSplit() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(MULTIPLE_ACK_REQUESTS_SPLIT_FIRST_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        assertNull(msg.getContent());
        Assert.assertEquals(1, msg.getCSeq().getSeqNumber());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        assertNull(msg.getContent());
        Assert.assertEquals(2, msg.getCSeq().getSeqNumber());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNull(msg);    
        byteBuf = Unpooled.wrappedBuffer(MULTIPLE_ACK_REQUESTS_SPLIT_SECOND_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        Assert.assertEquals(3, msg.getCSeq().getSeqNumber());
        assertNull(msg.getContent());        
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        Assert.assertEquals(4, msg.getCSeq().getSeqNumber());
        assertNull(msg.getContent());         
    }

    public void testMultipleACKRequestsSplitInit() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(MULTIPLE_ACK_REQUESTS_SPLIT_FIRST_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        assertNull(msg.getContent());
        Assert.assertEquals(1, msg.getCSeq().getSeqNumber());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        assertNull(msg.getContent());
        Assert.assertEquals(2, msg.getCSeq().getSeqNumber());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNull(msg);    
        byteBuf = Unpooled.wrappedBuffer(MULTIPLE_ACK_REQUESTS_SPLIT_SECOND_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        Assert.assertEquals(3, msg.getCSeq().getSeqNumber());
        assertNull(msg.getContent());        
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        Assert.assertEquals(4, msg.getCSeq().getSeqNumber());
        assertNull(msg.getContent());         
        byteBuf = Unpooled.wrappedBuffer(MULTIPLE_ACK_REQUESTS_SPLIT_THIRD_PART.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());                
        msg = parser.consumeSIPMessage();  
        assertNull(msg);          
        byteBuf = Unpooled.wrappedBuffer(byteBuf, Unpooled.wrappedBuffer(MULTIPLE_ACK_REQUESTS_SPLIT_FOURTH_PART.getBytes()));           
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();               
        assertNotNull(msg);
        Assert.assertEquals(5, msg.getCSeq().getSeqNumber());
        assertNull(msg.getContent());        
    }

    public void testMultipleResponses() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(MULTIPLE_RESPONSES.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();               
        Assert.assertEquals(180, ((SIPResponse)msg).getStatusCode());        
        ViaHeader via = (ViaHeader) msg.getHeader("Via");        
        assertNotNull(via);
        Assert.assertEquals("TCP", via.getTransport());
        assertNull(msg.getContent());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg3 = parser.consumeSIPMessage();               
        Assert.assertEquals(200, ((SIPResponse)msg3).getStatusCode());
        via = (ViaHeader) msg3.getHeader("Via");        
        Assert.assertEquals("TCP", via.getTransport());
        assertNull(msg3.getContent());
    }

    public void testMultipleResponsesParseException() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(MULTIPLE_RESPONSES_PARSE_EXCEPTION.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        boolean exceptionThrown = false;
        try {        
            parser.consumeSIPMessage();               
        } catch (ParseException e) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg3 = parser.consumeSIPMessage();               
        Assert.assertNotNull(msg3);
        Assert.assertEquals(200, ((SIPResponse)msg3).getStatusCode());
        ViaHeader via = (ViaHeader) msg3.getHeader("Via");        
        Assert.assertEquals("TCP", via.getTransport());
        assertNull(msg3.getContent());
    }

    public void testCRLF() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();
        Assert.assertTrue(msg.isNullRequest());
    }

    public void testDoubleCRLF() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(DOUBLE_CRLF.getBytes());
        Assert.assertTrue(parser.parseBytes(byteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertTrue(msg.isNullRequest());
    }

    public void testNormalBodySeparation() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(HEADER_CHUNK.getBytes());
        ByteBuf emptyLineByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(BODY_CHUNK.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf, emptyLineByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNotNull(msg);        
    }

    public void testSplitBody() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(HEADER_CHUNK.getBytes());
        ByteBuf emptyLineByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(SPLIT_BODY_CHUNK_FIRST_PART.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf, emptyLineByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNull(msg);        
        ByteBuf secondBodyPartByteBuf = Unpooled.wrappedBuffer(SPLIT_BODY_CHUNK_SECOND_PART.getBytes());
        messageByteBuf = Unpooled.wrappedBuffer(messageByteBuf.resetReaderIndex(), secondBodyPartByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();        
        Assert.assertNotNull(msg);        
    }

    public void testSplitBodyAndMultipleRequests() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(HEADER_CHUNK.getBytes());
        ByteBuf emptyLineByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(SPLIT_BODY_CHUNK_FIRST_PART.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf, emptyLineByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNull(msg);        
        ByteBuf secondBodyPartByteBuf = Unpooled.wrappedBuffer(SPLIT_BODY_CHUNK_SECOND_PART.getBytes());
        messageByteBuf = Unpooled.wrappedBuffer(messageByteBuf.resetReaderIndex(), secondBodyPartByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());                       
        msg = parser.consumeSIPMessage();        
        Assert.assertNotNull(msg);
        ByteBuf doubleCRLFByteBuf = Unpooled.wrappedBuffer(DOUBLE_CRLF.getBytes());
        ByteBuf fullMessageByteBuf = Unpooled.wrappedBuffer(IPV6MESSAGE.getBytes());
        messageByteBuf = Unpooled.wrappedBuffer(doubleCRLFByteBuf, fullMessageByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();                
        Assert.assertTrue(msg.isNullRequest());
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();                
        Assert.assertFalse(msg.isNullRequest());
        RecordRoute routeHdr = (RecordRoute) msg.getHeader("Record-Route");
        SipUri uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));        
    }

    public void testCompactBodySeparation() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(HEADER_CHUNK_COMPACT.getBytes());
        ByteBuf emptyLineByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(BODY_CHUNK.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf, emptyLineByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNotNull(msg);  
    }
    
    public void testHeaderSeparationAtChunkEndAndContentLengthNotLastHeader() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(INVITE_HEADER_CHUNK.getBytes());
        ByteBuf contentLenghtheaderByteBuf = Unpooled.wrappedBuffer(CONTENT_LENGTH_HEADER.getBytes());
        ByteBuf header1ByteBuf = Unpooled.wrappedBuffer(HEADER1.getBytes());
        ByteBuf header2ByteBuf = Unpooled.wrappedBuffer(HEADER2.getBytes());
        ByteBuf emptyLineByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(BODY_CHUNK.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf, contentLenghtheaderByteBuf, header1ByteBuf, header2ByteBuf, emptyLineByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNotNull(msg);  
        AllowHeader allowHeader = (AllowHeader) msg.getHeader("Allow");
        Assert.assertNotNull(allowHeader);  
        Assert.assertNotNull(msg.getContent());
    }

    public void testBodySeparationAtChunkEnd() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(HEADER_CHUNK.getBytes());
        ByteBuf crByteBuf = Unpooled.wrappedBuffer("\r".getBytes());
        ByteBuf lfByteBuf = Unpooled.wrappedBuffer("\n".getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(BODY_CHUNK.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf, crByteBuf, lfByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNotNull(msg);                  
    }

    public void testSingleCRLFAndMessage() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf singleCRLFByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf fullMessageByteBuf = Unpooled.wrappedBuffer(IPV6MESSAGE.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(singleCRLFByteBuf, fullMessageByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();                
        Assert.assertTrue(msg.isNullRequest());
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();                
        Assert.assertFalse(msg.isNullRequest());
        RecordRoute routeHdr = (RecordRoute) msg.getHeader("Record-Route");
        SipUri uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));
    }

    public void testDoubleCRLFAndMessage() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf doubleCRLFByteBuf = Unpooled.wrappedBuffer(DOUBLE_CRLF.getBytes());
        ByteBuf fullMessageByteBuf = Unpooled.wrappedBuffer(IPV6MESSAGE.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(doubleCRLFByteBuf, fullMessageByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();                
        Assert.assertTrue(msg.isNullRequest());
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();                
        Assert.assertFalse(msg.isNullRequest());
        RecordRoute routeHdr = (RecordRoute) msg.getHeader("Record-Route");
        SipUri uri = (SipUri) routeHdr.getAddress().getURI();
        Assert.assertEquals("fe80:0:0:0:20c:29ff:fe7d:7f9c%2", uri.getDecodedParam("node_host"));
    }

    public void testNormalBodySeparationButSplit() throws Exception {
        NettyMessageParser parser = new NettyMessageParser(SipStackImpl.MAX_DATAGRAM_SIZE, false);    
        ByteBuf headerByteBuf = Unpooled.wrappedBuffer(HEADER_CHUNK.getBytes());
        Assert.assertFalse(parser.parseBytes(headerByteBuf).isParsingComplete());        
        SIPMessage msg = parser.consumeSIPMessage();        
        Assert.assertNull(msg);        
        ByteBuf emptyLineByteBuf = Unpooled.wrappedBuffer(CRLF.getBytes());
        ByteBuf bodyByteBuf = Unpooled.wrappedBuffer(BODY_CHUNK.getBytes());
        ByteBuf messageByteBuf = Unpooled.wrappedBuffer(headerByteBuf.resetReaderIndex(), emptyLineByteBuf, bodyByteBuf);
        Assert.assertTrue(parser.parseBytes(messageByteBuf).isParsingComplete());        
        msg = parser.consumeSIPMessage();                        
        Assert.assertNotNull(msg);        
    }

}
