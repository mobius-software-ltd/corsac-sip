package test.unit.gov.nist.javax.sip.stack.fallback;

import javax.sip.Dialog;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.message.Request;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.IOExceptionEventExt;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.HopImpl;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import gov.nist.javax.sip.stack.SIPTransaction;
import gov.nist.javax.sip.stack.transports.processors.MessageChannel;

/**
 * Sends a UDP message refused for its size (MessageToLong) again over TCP: a
 * response from the same server transaction, a request in a new client transaction with CSeq + 1. 
 */
final class Fallback {

    private static final StackLogger logger = CommonLogger.getLogger(Fallback.class);

    private Fallback() {
    }

    /** true if the message went out again over TCP */
    static boolean fallBack(IOExceptionEvent event, SipStackImpl sipStack, SipProviderImpl sipProvider,
            String myAddress, int myPort) {
        if (!(event instanceof IOExceptionEventExt)) {
            return false;
        }
        IOExceptionEventExt eventExt = (IOExceptionEventExt) event;
        if (eventExt.getReason() != IOExceptionEventExt.Reason.MessageToLong
                || !ListeningPoint.UDP.equalsIgnoreCase(eventExt.getTransport())) {
            return false;
        }
        SIPMessage message = (SIPMessage) eventExt.getMessage();
        SIPTransaction transaction;
        if (message instanceof SIPRequest) {
            transaction = (SIPTransaction) ((SIPRequest) message).getTransaction();
        } else {
            transaction = sipStack.findTransaction(((SIPResponse) message).getTransactionId(), true);
        }
        if (transaction == null) {
            logger.logError("fallback: no transaction for " + message.getFirstLine());
            return false;
        }
        try {
            HopImpl tcpHop = new HopImpl(eventExt.getPeerHost(), eventExt.getPeerPort(), ListeningPoint.TCP);
            MessageChannel tcpChannel = sipStack.createRawMessageChannel(myAddress, myPort, tcpHop);
            logger.logInfo("fallback: again over TCP " + message.getFirstLine());
            transaction.setEncapsulatedChannel(tcpChannel);
            if (message instanceof SIPResponse) {
                ((SIPServerTransaction) transaction).sendResponse((SIPResponse) message);
                return true;
            }
            SIPRequest request = (SIPRequest) message;
            if (Request.ACK.equals(request.getMethod())) {
                return false;
            }
            Dialog dialog = transaction.getDialog();
            request.setTransaction(null);
            request.getCSeq().setSeqNumber(request.getCSeq().getSeqNumber() + 1);
            sipStack.removeTransaction(transaction);
            transaction.disableRetransmissionTimer();
            transaction.disableTimeoutTimer();
            SIPClientTransaction newTransaction = sipStack.createClientTransaction(request, sipProvider, tcpChannel);
            newTransaction.setEncapsulatedChannel(tcpChannel);
            newTransaction.setNextHop(tcpHop);
            sipStack.addTransaction(newTransaction);
            if (dialog == null || Request.CANCEL.equals(request.getMethod())) {
                newTransaction.sendRequest();
            } else {
                ((SIPDialog) dialog).sendRequest(newTransaction, true);
            }
            return true;
        } catch (Exception ex) {
            logger.logError("fallback: could not send again over TCP " + message.getFirstLine(), ex);
            return false;
        }
    }
}
