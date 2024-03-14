package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.Iterator;

import gov.nist.javax.sip.header.*;

/**
 * This interface represents the Target-Dialog SIP header list, as defined by RFC 4538.
 * @author ValeriiaMukha
 */
public final class TargetDialogList extends SIPHeaderList<TargetDialog> {
    
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor, requires Call-Id to be set during instantiation.
     * 
     * @param callId the Call-Id value to set for all TargetDialogs in the list
     */
    public TargetDialogList(String callId) throws ParseException {
        super(TargetDialog.class, TargetDialogHeader.NAME);
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
}
