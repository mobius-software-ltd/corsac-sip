package test.unit.gov.nist.javax.sip.stack.malformed;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import test.tck.msgflow.callflows.TestAssertion;
import test.unit.gov.nist.javax.sip.stack.malformed.Records.MessageRecord;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 * No stack: its encoder drops unparsed headers, so the INVITE goes out as raw bytes.
 */
public class Shootist {

    private static final StackLogger logger = CommonLogger.getLogger(Shootist.class);
    private static final String myAddress = "127.0.0.1";

    /** as in the capture, no closing '>' */
    static final String MALFORMED_P_PREFERRED_IDENTITY = "P-Preferred-Identity: <sip:+41555521580@sips.peoplefone.ch";

    /** top Via branch from the capture */
    private static final String BRANCH = "z9hG4bKec96.917cddc0a4184a5280db5ba975a8f35f.0";

    private static final String REQUEST_URI = "sip:+41817500770@95.128.84.2:5060";
    private static final String FROM = "From: \"Matteo Facino\" <sip:+41555521580@50.93.36.47>;tag=as0f75d8b4";
    private static final String TO = "To: <sip:+41817500770@sips.peoplefone.ch:5060>";
    private static final String CALL_ID = "73bda81168f72f7a1d741c63044e1643";
    private static final long CSEQ = 103;

    private static final String OFFER_SDP = "v=0\r\n" + "o=root 1077366851 1077366852 IN IP4 95.128.80.6\r\n"
            + "s=Wildix 6.10.20260423.3~7b5690fd\r\n" + "c=IN IP4 95.128.80.6\r\n" + "t=0 0\r\n"
            + "m=audio 31822 RTP/AVP 8 0 101\r\n" + "a=rtpmap:8 PCMA/8000\r\n" + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=rtpmap:101 telephone-event/8000\r\n" + "a=fmtp:101 0-16\r\n" + "a=sendrecv\r\n"
            + "a=rtcp:31823\r\n" + "a=ptime:20\r\n";

    private final int port;
    private final int peerPort;

    private final DatagramSocket socket;
    private final MessageFactoryImpl messageFactory = new MessageFactoryImpl();

    private final List<MessageRecord> receivedResponses = Collections
            .synchronizedList(new ArrayList<MessageRecord>());

    public Shootist(int port, int peerPort) {
        this.port = port;
        this.peerPort = peerPort;
        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(myAddress));
        } catch (Exception ex) {
            throw new RuntimeException("could not create shootist", ex);
        }
        Thread receiver = new Thread(new Runnable() {
            @Override
            public void run() {
                receive();
            }
        }, "shootist-" + port);
        receiver.setDaemon(true);
        receiver.start();
    }

    /** INVITE from the capture with identityHeader, top Via is ours so the response comes back here */
    public void sendInvite(String identityHeader) throws IOException {
        String invite = "INVITE " + REQUEST_URI + " SIP/2.0\r\n"
                + "Record-Route: <sip:95.128.80.3;lr=on;did=cfa.d8d6>\r\n"
                + topVia() + "\r\n"
                + "Via: SIP/2.0/UDP 50.93.36.47;received=50.93.36.47;branch=z9hG4bKec96.3ba95b94e819d1501651b01423e8cfbf.0;rport=5060\r\n"
                + "Max-Forwards: 68\r\n"
                + FROM + "\r\n"
                + TO + "\r\n"
                + "Contact: <sip:+41555521580@50.93.36.47;transport=UDP;alias=50.93.36.47~5060~1>\r\n"
                + "Call-ID: " + CALL_ID + "\r\n"
                + "CSeq: " + CSEQ + " INVITE\r\n"
                + "User-Agent: WildixGW 6.10.20260423.3~7b5690fd\r\n"
                + "Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REFER, SUBSCRIBE, NOTIFY, INFO, PUBLISH\r\n"
                + "Supported: replaces\r\n"
                + "X-Role: callee\r\n"
                + "X-Session-ID: 1792396591.105\r\n"
                + identityHeader + "\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: " + OFFER_SDP.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "X-Next-Hop: sips.peoplefone.ch\r\n"
                + "X-Next-Hop-Port: 5060\r\n"
                + "X-Dst-Addr: extern\r\n"
                + "X-DirectRTP: false\r\n"
                + "x-username: 90705971061\r\n"
                + "X-src-ip: 50.93.36.47\r\n"
                + "\r\n"
                + OFFER_SDP;
        logger.logInfo("shootist:" + port + " sending INVITE with " + identityHeader);
        send(invite);
    }

    /** RFC 3261 17.1.1.3: ACK to a non-2xx final response, same branch, To tag of the response */
    private void sendAck(MessageRecord response) throws IOException {
        String ack = "ACK " + REQUEST_URI + " SIP/2.0\r\n"
                + topVia() + "\r\n"
                + "Max-Forwards: 70\r\n"
                + FROM + "\r\n"
                + TO + (response.toTag != null ? ";tag=" + response.toTag : "") + "\r\n"
                + "Call-ID: " + CALL_ID + "\r\n"
                + "CSeq: " + CSEQ + " ACK\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n";
        send(ack);
    }

    private String topVia() {
        return "Via: SIP/2.0/UDP " + myAddress + ":" + port + ";branch=" + BRANCH;
    }

    private void send(String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(myAddress), peerPort));
    }

    private void receive() {
        byte[] buffer = new byte[65535];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException ex) {
                // socket closed by stop()
                return;
            }
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            try {
                MessageRecord record = Records.record(messageFactory.createResponse(message));
                receivedResponses.add(record);
                logger.logInfo("shootist:" + port + " received " + record);
                if (record.status >= 300 && Request.INVITE.equals(record.method)) {
                    sendAck(record);
                }
            } catch (Exception ex) {
                logger.logError("shootist: could not handle what came back:\n" + message, ex);
            }
        }
    }

    public List<MessageRecord> getFinalInviteResponses() {
        return Records.finalResponses(receivedResponses, Request.INVITE);
    }

    public TestAssertion getFinalInviteResponseAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return !getFinalInviteResponses().isEmpty();
            }
        };
    }

    public void stop() {
        socket.close();
    }
}
