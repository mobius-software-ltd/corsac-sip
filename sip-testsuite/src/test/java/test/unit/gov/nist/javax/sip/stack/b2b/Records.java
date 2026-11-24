package test.unit.gov.nist.javax.sip.stack.b2b;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.RSeqHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

/** B2B relevant bits of a message, copied out so nobody holds on to stack objects. */
final class Records {

    private Records() {
    }

    static final class MessageRecord {
        final String method;
        final long cseq;
        /** 0 for requests */
        final int status;
        final boolean rseq;
        final boolean require100rel;
        /** null when no Event header */
        final String event;
        /** null when no Expires header */
        final Integer expires;
        /** null when no Subscription-State header */
        final String subscriptionState;
        final boolean retransmission;

        // dialog bits, to check that the legs are independent
        final String callId;
        /** null for responses */
        final String requestUri;
        final String fromTag;
        final String toTag;
        /** top Via as host:port */
        final String viaSentBy;
        final String viaBranch;
        /** Contact URI, null when none */
        final String contact;
        /** Route URIs in header order */
        final List<String> routes;
        /** Record-Route URIs in header order */
        final List<String> recordRoutes;

        MessageRecord(Message message, boolean retransmission) {
            CSeqHeader cseqHeader = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
            status = message instanceof Response ? ((Response) message).getStatusCode() : 0;
            method = cseqHeader != null ? cseqHeader.getMethod()
                    : (message instanceof Request ? ((Request) message).getMethod() : null);
            cseq = cseqHeader != null ? cseqHeader.getSeqNumber() : -1;
            rseq = message.getHeader(RSeqHeader.NAME) != null;
            require100rel = hasOptionTag(message, RequireHeader.NAME, "100rel");
            EventHeader eventHeader = (EventHeader) message.getHeader(EventHeader.NAME);
            event = eventHeader != null ? eventHeader.getEventType() : null;
            ExpiresHeader expiresHeader = (ExpiresHeader) message.getHeader(ExpiresHeader.NAME);
            expires = expiresHeader != null ? Integer.valueOf(expiresHeader.getExpires()) : null;
            SubscriptionStateHeader state = (SubscriptionStateHeader) message
                    .getHeader(SubscriptionStateHeader.NAME);
            subscriptionState = state != null ? state.getState() : null;
            this.retransmission = retransmission;

            CallIdHeader callIdHeader = (CallIdHeader) message.getHeader(CallIdHeader.NAME);
            callId = callIdHeader != null ? callIdHeader.getCallId() : null;
            requestUri = message instanceof Request ? ((Request) message).getRequestURI().toString() : null;
            FromHeader from = (FromHeader) message.getHeader(FromHeader.NAME);
            fromTag = from != null ? from.getTag() : null;
            ToHeader to = (ToHeader) message.getHeader(ToHeader.NAME);
            toTag = to != null ? to.getTag() : null;
            ViaHeader via = (ViaHeader) message.getHeader(ViaHeader.NAME);
            viaSentBy = via != null ? via.getHost() + ":" + via.getPort() : null;
            viaBranch = via != null ? via.getBranch() : null;
            ContactHeader contactHeader = (ContactHeader) message.getHeader(ContactHeader.NAME);
            contact = contactHeader != null ? contactHeader.getAddress().getURI().toString() : null;
            routes = uris(message.getHeaders(RouteHeader.NAME));
            recordRoutes = uris(message.getHeaders(RecordRouteHeader.NAME));
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

        /** method for requests, status/method for responses */
        String name() {
            return isRequest() ? method : status + "/" + method;
        }

        @Override
        public String toString() {
            return name() + " cseq=" + cseq + (rseq ? " RSeq" : "") + (require100rel ? " Require:100rel" : "")
                    + (event != null ? " Event=" + event : "") + (expires != null ? " Expires=" + expires : "")
                    + (subscriptionState != null ? " Subscription-State=" + subscriptionState : "")
                    + (contact != null ? " Contact=" + contact : "") + (retransmission ? " RETRANS" : "");
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
            if (h instanceof RequireHeader && tag.equalsIgnoreCase(((RequireHeader) h).getOptionTag())) {
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

    /** names in arrival order, retransmissions left out */
    static List<String> names(List<MessageRecord> records) {
        List<String> out = new ArrayList<String>();
        synchronized (records) {
            for (MessageRecord r : records) {
                if (!r.retransmission) {
                    out.add(r.name());
                }
            }
        }
        return out;
    }
}
