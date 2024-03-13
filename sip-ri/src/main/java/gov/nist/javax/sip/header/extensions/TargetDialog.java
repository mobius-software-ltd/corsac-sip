package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.Iterator;
import javax.sip.header.ExtensionHeader;
import gov.nist.javax.sip.header.*;

/**
 * SIP Target-Dialog header implementation.
 */
public class TargetDialog extends AddressParametersHeader implements ExtensionHeader, TargetDialogHeader {
 
    private static final long serialVersionUID = 1L;
	protected CallIdentifier callId;
    public static final String NAME = SIPHeaderNames.TARGET_DIALOG;

    /**
     * Default constructor.
     */
    public TargetDialog() { 
        super(NAME);
    }

    /**
     * Constructor with CallIdentifier.
     *
     * @param cid CallIdentifier to set
     */
    public TargetDialog(CallIdentifier cid) {
        super(NAME);
        callId = cid;
    }

    /**
     * Sets the Call-Id of the Target-Dialog header.
     *
     * @param callId the Call-Id value to set
     * @throws ParseException if the provided callId cannot be parsed
     */
    public void setCallId(String callId) throws ParseException {
        try {
            // Instantiating a new CallIdentifier object using the provided callId
            this.callId = new CallIdentifier(callId);
        } catch (Exception e) {
            // If an exception occurs during instantiation, throw a ParseException
            throw new ParseException(e.getMessage(), 0);
        }
    }

    /**
     * Gets the Call-Id value of the Target-Dialog header.
     *
     * @return the Call-Id value
     */
    public String getCallId() {
        return callId != null ? callId.encode() : null;
    }

    @Override
    public StringBuilder encodeBody(StringBuilder retval) {
        if (callId != null) {
            return callId.encode(retval);
        }
        return retval;
    }

    @Override
    public Object clone() {
        TargetDialog retval = (TargetDialog) super.clone();
        if (callId != null) {
            retval.callId = (CallIdentifier) callId.clone();
        }
        return retval;
    }

    @Override
    public void setValue(String value) throws ParseException {
        // Not implemented yet, throw an exception to indicate it
        throw new UnsupportedOperationException("setValue method is not implemented yet");
    }

    @Override
    public String getParameter(String name) {
        // Not implemented yet
        return null;
    }

    @Override
    public void setParameter(String name, String value) throws ParseException {
        // Not implemented yet
    }

    @Override
    public Iterator<String> getParameterNames() {
        // Not implemented yet
        return null;
    }
 
    @Override
    public void removeParameter(String name) {
        // Not implemented yet
    }
}

