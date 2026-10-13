package test.unit.gov.nist.javax.sip.stack.retransmissiontimer;

import java.util.ArrayList;
import java.util.List;

import javax.sip.header.CSeqHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

/** Relevant bits of a message, copied out so nobody holds on to stack objects. */
final class Records {

    private Records() {
    }

    static final class MessageRecord {
        final String method;
        final long cseq;
        /** 0 for requests */
        final int status;

        MessageRecord(Message message) {
            CSeqHeader cseqHeader = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
            status = message instanceof Response ? ((Response) message).getStatusCode() : 0;
            method = cseqHeader != null ? cseqHeader.getMethod()
                    : (message instanceof Request ? ((Request) message).getMethod() : null);
            cseq = cseqHeader != null ? cseqHeader.getSeqNumber() : -1;
        }

        boolean isRequest() {
            return status == 0;
        }

        @Override
        public String toString() {
            return (isRequest() ? method : status + "/" + method) + " cseq=" + cseq;
        }
    }

    static MessageRecord record(Message message) {
        return new MessageRecord(message);
    }

    static List<MessageRecord> requests(List<MessageRecord> records, String method) {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (records) {
            for (MessageRecord r : records) {
                if (r.isRequest() && method.equals(r.method)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    static List<MessageRecord> responses(List<MessageRecord> records, int status, String method) {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (records) {
            for (MessageRecord r : records) {
                if (!r.isRequest() && r.status == status && method.equals(r.method)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    static List<MessageRecord> provisionalResponses(List<MessageRecord> records, String method) {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (records) {
            for (MessageRecord r : records) {
                if (!r.isRequest() && r.status < 200 && method.equals(r.method)) {
                    out.add(r);
                }
            }
        }
        return out;
    }
}
