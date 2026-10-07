package test.unit.gov.nist.javax.sip.stack.rfc4028;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
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
        final boolean require100rel;
        final boolean retransmission;

        // dialog bits, to check what the stack builds on its own against what the application sent
        final String callId;
        /** null for responses */
        final String requestUri;
        final String fromUri;
        final String fromTag;
        final String toUri;
        final String toTag;
        /** top Via as host:port */
        final String viaSentBy;
        final String viaBranch;
        /** null when no Max-Forwards header */
        final Integer maxForwards;
        /** Contact URI, null when none */
        final String contact;
        /** Route URIs in header order */
        final List<String> routes;
        /** Record-Route URIs in header order */
        final List<String> recordRoutes;
        /** type/subtype, null when none */
        final String contentType;
        /** Content-Length value, -1 when no header */
        final int contentLength;

        MessageRecord(Message message, boolean retransmission) {
            CSeqHeader cseqHeader = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
            status = message instanceof Response ? ((Response) message).getStatusCode() : 0;
            method = cseqHeader != null ? cseqHeader.getMethod()
                    : (message instanceof Request ? ((Request) message).getMethod() : null);
            cseq = cseqHeader != null ? cseqHeader.getSeqNumber() : -1;
            byte[] raw = message.getRawContent();
            body = raw != null && raw.length > 0 ? new String(raw) : null;

            SessionExpiresHeader se = (SessionExpiresHeader) message.getHeader(SessionExpiresHeader.NAME);
            seExpires = se != null ? Integer.valueOf(se.getExpires()) : null;
            seRefresher = se != null ? se.getRefresher() : null;
            MinSE minSeHeader = (MinSE) message.getHeader(MinSE.NAME);
            minSe = minSeHeader != null ? Integer.valueOf(minSeHeader.getExpires()) : null;
            supportedTimer = hasOptionTag(message, SupportedHeader.NAME, "timer");
            requireTimer = hasOptionTag(message, RequireHeader.NAME, "timer");
            require100rel = hasOptionTag(message, RequireHeader.NAME, "100rel");
            this.retransmission = retransmission;

            CallIdHeader callIdHeader = (CallIdHeader) message.getHeader(CallIdHeader.NAME);
            callId = callIdHeader != null ? callIdHeader.getCallId() : null;
            requestUri = message instanceof Request ? ((Request) message).getRequestURI().toString() : null;
            FromHeader from = (FromHeader) message.getHeader(FromHeader.NAME);
            fromUri = from != null ? from.getAddress().getURI().toString() : null;
            fromTag = from != null ? from.getTag() : null;
            ToHeader to = (ToHeader) message.getHeader(ToHeader.NAME);
            toUri = to != null ? to.getAddress().getURI().toString() : null;
            toTag = to != null ? to.getTag() : null;
            ViaHeader via = (ViaHeader) message.getHeader(ViaHeader.NAME);
            viaSentBy = via != null ? via.getHost() + ":" + via.getPort() : null;
            viaBranch = via != null ? via.getBranch() : null;
            MaxForwardsHeader mf = (MaxForwardsHeader) message.getHeader(MaxForwardsHeader.NAME);
            maxForwards = mf != null ? Integer.valueOf(mf.getMaxForwards()) : null;
            ContactHeader contactHeader = (ContactHeader) message.getHeader(ContactHeader.NAME);
            contact = contactHeader != null ? contactHeader.getAddress().getURI().toString() : null;
            routes = uris(message.getHeaders(RouteHeader.NAME));
            recordRoutes = uris(message.getHeaders(RecordRouteHeader.NAME));
            ContentTypeHeader ct = (ContentTypeHeader) message.getHeader(ContentTypeHeader.NAME);
            contentType = ct != null ? ct.getContentType() + "/" + ct.getContentSubType() : null;
            ContentLengthHeader cl = (ContentLengthHeader) message.getHeader(ContentLengthHeader.NAME);
            contentLength = cl != null ? cl.getContentLength() : -1;
        }

        private static List<String> uris(ListIterator<?> headers) {
            List<String> out = new ArrayList<String>();
            while (headers != null && headers.hasNext()) {
                Object h = headers.next();
                if (h instanceof RouteHeader) {
                    out.add(((RouteHeader) h).getAddress().getURI().toString());
                } else if (h instanceof RecordRouteHeader) {
                    out.add(((RecordRouteHeader) h).getAddress().getURI().toString());
                }
            }
            return out;
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
                    + (requireTimer ? " Require:timer" : "") + (require100rel ? " Require:100rel" : "")
                    + (contact != null ? " Contact=" + contact : "") + (routes.isEmpty() ? "" : " Route=" + routes)
                    + (body != null ? " body[" + body.length() + "]" : " nobody")
                    + (retransmission ? " RETRANS" : "");
        }
    }

    static MessageRecord record(Message message) {
        return new MessageRecord(message, false);
    }

    static MessageRecord record(Message message, boolean retransmission) {
        return new MessageRecord(message, retransmission);
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
