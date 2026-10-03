package test.unit.gov.nist.javax.sip.stack.rfc4028;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.sip.header.AllowHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;

/** RFC 4028 relevant bits of a message, copied out so nobody holds on to stack objects. */
final class Records {

    private Records() {
    }

    static final class MessageRecord {
        final String method;
        final long cseq;
        /** 0 for requests */
        final int status;
        final String body;
        /** null when no Session-Expires header */
        final Integer seExpires;
        final String seRefresher;
        /** null when no Min-SE header */
        final Integer minSe;
        final boolean supportedTimer;
        final boolean requireTimer;
        final boolean allowUpdate;
        final boolean retransmission;

        MessageRecord(String method, long cseq, int status, String body, Integer seExpires, String seRefresher,
                Integer minSe, boolean supportedTimer, boolean requireTimer, boolean allowUpdate,
                boolean retransmission) {
            this.method = method;
            this.cseq = cseq;
            this.status = status;
            this.body = body;
            this.seExpires = seExpires;
            this.seRefresher = seRefresher;
            this.minSe = minSe;
            this.supportedTimer = supportedTimer;
            this.requireTimer = requireTimer;
            this.allowUpdate = allowUpdate;
            this.retransmission = retransmission;
        }

        boolean isRequest() {
            return status == 0;
        }

        boolean hasSessionExpires() {
            return seExpires != null;
        }

        @Override
        public String toString() {
            return (isRequest() ? method : status + "/" + method) + " cseq=" + cseq
                    + (seExpires != null ? " SE=" + seExpires + ";refresher=" + seRefresher : " noSE")
                    + (minSe != null ? " MinSE=" + minSe : "") + (supportedTimer ? " Supported:timer" : "")
                    + (requireTimer ? " Require:timer" : "") + (allowUpdate ? " Allow:UPDATE" : "")
                    + (body != null ? " body[" + body.length() + "]" : " nobody")
                    + (retransmission ? " RETRANS" : "");
        }
    }

    static MessageRecord record(Message message) {
        return record(message, false);
    }

    static MessageRecord record(Message message, boolean retransmission) {
        CSeqHeader cseq = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
        int status = message instanceof Response ? ((Response) message).getStatusCode() : 0;
        String method = cseq != null ? cseq.getMethod()
                : (message instanceof Request ? ((Request) message).getMethod() : null);
        long seq = cseq != null ? cseq.getSeqNumber() : -1;
        byte[] raw = message.getRawContent();
        String body = raw != null && raw.length > 0 ? new String(raw) : null;

        SessionExpiresHeader se = (SessionExpiresHeader) message.getHeader(SessionExpiresHeader.NAME);
        Integer seExpires = se != null ? Integer.valueOf(se.getExpires()) : null;
        String seRefresher = se != null ? se.getRefresher() : null;

        MinSE minSe = (MinSE) message.getHeader(MinSE.NAME);
        Integer minSeValue = minSe != null ? Integer.valueOf(minSe.getExpires()) : null;

        return new MessageRecord(method, seq, status, body, seExpires, seRefresher, minSeValue,
                hasOptionTag(message, SupportedHeader.NAME, "timer"), hasOptionTag(message, RequireHeader.NAME, "timer"),
                allowsUpdate(message), retransmission);
    }

    static boolean hasOptionTag(Message message, String headerName, String tag) {
        ListIterator<?> it = message.getHeaders(headerName);
        while (it != null && it.hasNext()) {
            Object h = it.next();
            String optionTag = null;
            if (h instanceof SupportedHeader) {
                optionTag = ((SupportedHeader) h).getOptionTag();
            } else if (h instanceof RequireHeader) {
                optionTag = ((RequireHeader) h).getOptionTag();
            }
            if (tag.equalsIgnoreCase(optionTag)) {
                return true;
            }
        }
        return false;
    }

    static boolean allowsUpdate(Message message) {
        ListIterator<?> it = message.getHeaders(AllowHeader.NAME);
        while (it != null && it.hasNext()) {
            if (Request.UPDATE.equalsIgnoreCase(((AllowHeader) it.next()).getMethod())) {
                return true;
            }
        }
        return false;
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

    /** one per CSeq, retransmissions folded */
    static List<MessageRecord> distinctResponses(List<MessageRecord> records, int status, String method) {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        List<Long> seen = new ArrayList<Long>();
        for (MessageRecord r : responses(records, status, method)) {
            if (!seen.contains(Long.valueOf(r.cseq))) {
                seen.add(Long.valueOf(r.cseq));
                out.add(r);
            }
        }
        return out;
    }
}
