package gov.nist.javax.sip.stack;

import java.io.IOException;
import java.io.Serializable;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.stack.timers.SIPStackTimerTask;

public class SIPDialogTimerTask extends SIPStackTimerTask implements Serializable {
    private static StackLogger logger = CommonLogger.getLogger(SIPDialogTimerTask.class);    
    private static final long serialVersionUID = 1L;
    protected SIPDialog dialog;
    protected int nRetransmissions;
    protected int timerT2;
    protected long baseTimerInterval;
    protected SIPServerTransaction transaction;
    
    public SIPDialogTimerTask(SIPDialog sipDialog,SIPServerTransaction transaction, int timerT2,long baseTimerInterval) {
        	super(SIPDialogTimerTask.class.getSimpleName());
            this.dialog = sipDialog;
            nRetransmissions = 0;
            this.timerT2 = timerT2;
            this.baseTimerInterval = baseTimerInterval;
            this.transaction = transaction;
    }

    public void runTask() {
        // If I ACK has not been seen on Dialog,
        // resend last response.
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
            logger.logDebug("Running dialog timer");
        if(nRetransmissions==0)
        	nRetransmissions = 1;
        else
        	nRetransmissions *= 2;
        
        SIPServerTransaction transaction = (SIPServerTransaction) 
                dialog.getStack().findTransaction(getTaskName(), true);
        
        /*
         * Issue 106. Section 13.3.1.4 RFC 3261 The 2xx response is passed
         * to the transport with an interval that starts at T1 seconds and
         * doubles for each retransmission until it reaches T2 seconds If
         * the server retransmits the 2xx response for 64T1 seconds without
         * receiving an ACK, the dialog is confirmed, but the session SHOULD
         * be terminated.
         */

        logger.logDebug(getId());
        if (nRetransmissions > dialog.getStack().getAckTimeoutFactor()
                * SIPTransaction.T1) {
            if (dialog.getSipProvider().getSipListener() != null
                    && dialog.getSipProvider().getSipListener() instanceof SipListenerExt) {
                dialog.raiseErrorEvent(SIPDialogErrorEvent.DIALOG_ACK_NOT_RECEIVED_TIMEOUT);
                return;
            } else {
                dialog.delete();
            }
            if (transaction != null
                    && transaction.getState() != javax.sip.TransactionState.TERMINATED) {
                transaction
                        .raiseErrorEvent(SIPTransactionErrorEvent.TIMEOUT_ERROR);
            }
        } else if ((transaction != null) && (!dialog.isAckSeen())) {
            // Retransmit to 2xx until ack receivedialog.
            if (dialog.lastResponseStatusCode.intValue() / 100 == 2) {
                try {

                    // resend the last response.
                    transaction.resendLastResponse();
                } catch (IOException ex) {

                    dialog.raiseIOException(transaction.getLastResponse(), gov.nist.javax.sip.IOExceptionEventExt.Reason.ConnectionError,
                            transaction.getHost(), transaction.getPort(), transaction.getPeerAddress(),
                            transaction.getPeerPort(), transaction
                                    .getPeerProtocol());

                } catch (MessageTooLongException ex) {

                    dialog.raiseIOException(transaction.getLastResponse(), gov.nist.javax.sip.IOExceptionEventExt.Reason.MessageToLong,
                            transaction.getHost(), transaction.getPort(), transaction.getPeerAddress(),
                            transaction.getPeerPort(), transaction
                                    .getPeerProtocol());

                } finally {                    
                    if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                        logger.logDebug(
                                "resend 200 response from " + dialog);
                    }
                }
            }
        }

        // Stop running this timer if the dialog is in the
        // confirmed state or ack seen if retransmit filter on.
        if (dialog.isAckSeen() || dialog.dialogState == SIPDialog.TERMINATED_STATE) {
            dialog.ongoingTransactionId = null;
            dialog.stopDialogTimer();            
        }
        else {
        	if (nRetransmissions <= timerT2)
        		dialog.rescheduleDialogTimer(nRetransmissions*baseTimerInterval, transaction);
            else
            	dialog.rescheduleDialogTimer(timerT2*baseTimerInterval, transaction);        	
        }
    }

    @Override
    public void cleanUpBeforeCancel() {
        dialog.ongoingTransactionId = null;
        // lastAckSent = null;
        dialog.cleanUpOnAck();
        super.cleanUpBeforeCancel();
    }

    @Override
    public String getId() {
        return dialog.getCallId().getCallId();
    }

    public int getNumberOfRetransmissions() {
        return nRetransmissions;
    }

    public void setNumberOfRetransmissions(int nRetransmissions) {
        this.nRetransmissions = nRetransmissions;
    }
}
