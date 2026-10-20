package test.unit.gov.nist.javax.sip.stack.malformed;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.sip.header.CSeqHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.javax.sip.message.SIPMessage;

/** Relevant bits of a message, copied out so nobody holds on to stack objects. */
final class Records {

    private Records() {
    }

    static final class MessageRecord {
        final String method;
        final long cseq;
        /** 0 for requests */
        final int status;
        /** null for requests */
        final String reasonPhrase;
        final String toTag;
        /** unrecognizedHeaders, as received */
        final List<String> unparsedHeaders;
        /** request as encodeAsBytes sends it on, null for responses */
        final String encoded;

        MessageRecord(Message message) {
            CSeqHeader cseqHeader = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
            status = message instanceof Response ? ((Response) message).getStatusCode() : 0;
            reasonPhrase = message instanceof Response ? ((Response) message).getReasonPhrase() : null;
            method = cseqHeader != null ? cseqHeader.getMethod()
                    : (message instanceof Request ? ((Request) message).getMethod() : null);
            cseq = cseqHeader != null ? cseqHeader.getSeqNumber() : -1;

            ToHeader to = (ToHeader) message.getHeader(ToHeader.NAME);
            toTag = to != null ? to.getTag() : null;

            unparsedHeaders = new ArrayList<String>();
            ListIterator<String> it = ((SIPMessage) message).getUnrecognizedHeaders();
            while (it.hasNext()) {
                unparsedHeaders.add(it.next());
            }
            // encodeAsBytes sets the top Via transport, so on a clone
            encoded = message instanceof Request
                    ? new String(((SIPMessage) ((SIPMessage) message).clone()).encodeAsBytes("UDP"))
                    : null;
        }

        boolean isRequest() {
            return status == 0;
        }

        boolean isFinal() {
            return status >= 200;
        }

        @Override
        public String toString() {
            return (isRequest() ? method : status + " " + reasonPhrase + "/" + method) + " cseq=" + cseq
                    + (unparsedHeaders.isEmpty() ? "" : " unparsed=" + unparsedHeaders);
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

    static List<MessageRecord> finalResponses(List<MessageRecord> records, String method) {
        List<MessageRecord> out = new ArrayList<MessageRecord>();
        synchronized (records) {
            for (MessageRecord r : records) {
                if (r.isFinal() && method.equals(r.method)) {
                    out.add(r);
                }
            }
        }
        return out;
    }
}
