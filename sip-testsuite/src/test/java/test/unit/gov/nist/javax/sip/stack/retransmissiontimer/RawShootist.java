package test.unit.gov.nist.javax.sip.stack.retransmissiontimer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.TestAssertion;

/**
 * UAC on a plain UDP socket, no stack: sends one INVITE and never ACKs the final response,
 * like a SIP scanner. Counts every 486 that reaches the socket.
 */
public class RawShootist {

    private static final Logger logger = LogManager.getLogger(RawShootist.class);
    private static final String myAddress = "127.0.0.1";

    private final int port;
    private final int peerPort;
    private final DatagramSocket socket;

    private final AtomicInteger busyHereReceived = new AtomicInteger();

    public RawShootist(int port, int peerPort) {
        this.port = port;
        this.peerPort = peerPort;
        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(myAddress));
        } catch (Exception ex) {
            throw new RuntimeException("could not create raw shootist", ex);
        }
        Thread receiver = new Thread(new Runnable() {
            @Override
            public void run() {
                receive();
            }
        }, "raw-shootist-" + port);
        receiver.setDaemon(true);
        receiver.start();
    }

    private void receive() {
        byte[] buffer = new byte[4096];
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String firstLine = message.split("\r\n", 2)[0];
                logger.info("raw shootist:" + port + " received " + firstLine);
                if (firstLine.startsWith("SIP/2.0 486")) {
                    busyHereReceived.incrementAndGet();
                }
            } catch (Exception ex) {
                return;
            }
        }
    }

    public void sendInvite() {
        String invite = "INVITE sip:shootme@" + myAddress + ":" + peerPort + " SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP " + myAddress + ":" + port + ";branch=z9hG4bK-noack-" + port + "\r\n"
                + "Max-Forwards: 70\r\n"
                + "From: <sip:rawshootist@" + myAddress + ":" + port + ">;tag=noack\r\n"
                + "To: <sip:shootme@" + myAddress + ":" + peerPort + ">\r\n"
                + "Call-ID: noack-" + port + "@" + myAddress + "\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Contact: <sip:rawshootist@" + myAddress + ":" + port + ">\r\n"
                + "Content-Length: 0\r\n\r\n";
        byte[] bytes = invite.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(myAddress), peerPort));
        } catch (Exception ex) {
            throw new RuntimeException("could not send raw INVITE", ex);
        }
    }

    public int getBusyHereCount() {
        return busyHereReceived.get();
    }

    public TestAssertion getBusyHereAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return busyHereReceived.get() > 0;
            }
        };
    }

    public void stop() {
        socket.close();
    }
}
