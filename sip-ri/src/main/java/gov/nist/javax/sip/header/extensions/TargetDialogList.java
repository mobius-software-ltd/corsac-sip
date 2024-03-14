package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.Iterator;

import gov.nist.javax.sip.header.*;

/**
 * This interface represents the Target-Dialog SIP header list, as defined by RFC 4538.
 */
public final class TargetDialogList extends SIPHeaderList<TargetDialog> {
    
    private static final long serialVersionUID = 1L;

	/**
     * Sets the Call-Id of the Target-Dialog header.
     * @author ValeriiaMukha
     *
     * @param callId the Call-Id value to set
     * @throws ParseException if the callId is invalid and cannot be parsed
     */
    void setCallId(String callId) throws ParseException {
        // Iterate over each TargetDialog in the list and set the Call-Id
        for (TargetDialog targetDialog : this) { 
            targetDialog.setCallId(callId); 
        }
    }

    @Override
    public Object clone() { 
        TargetDialogList retval = (TargetDialogList) super.clone();
        // Clone the elements of the list
        for (Iterator<TargetDialog> iterator = this.listIterator(); iterator.hasNext();) {
            TargetDialog targetDialog = iterator.next();
            retval.add((TargetDialog) targetDialog.clone());
        }
        return retval;
    }

    /** Default constructor */
    public TargetDialogList() {
        super(TargetDialog.class, TargetDialogHeader.NAME);
    }
}
