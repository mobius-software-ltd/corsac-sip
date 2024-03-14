package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.Iterator;
import gov.nist.core.NameValueList;
import javax.sip.header.ExtensionHeader;
import gov.nist.javax.sip.header.*;

/**
 * SIP Target-Dialog header implementation.
 *  @author ValeriiaMukha
 */
public class TargetDialog extends ParametersHeader implements ExtensionHeader, TargetDialogHeader {

    private static final long serialVersionUID = 1L;
    // Change access level to public
    public CallIdentifier callId;
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
            retval.append(callId.encode());
            // Encode parameters
            if (!parameters.isEmpty()) {
                retval.append(SEMICOLON).append(parameters.encode());
            }
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
        return parameters.getValue(name, true).toString();
    }

    @Override
    public void setParameter(String name, String value) throws ParseException {
        parameters.set(name, value);
    }

    @Override
    public Iterator<String> getParameterNames() {
        return parameters.getNames();
    }

    @Override 
    public void removeParameter(String name) {
        parameters.delete(name);
    }

    /**
     * Parses the header string into Call-Id and parameters.
     *
     * @param body the header string to parse
     * @throws ParseException if the header string cannot be parsed
     */
    public void decodeBody(String body) throws ParseException {
        int delimiter = body.indexOf(';');
        String callIdStr = (delimiter != -1) ? body.substring(0, delimiter) : body;
        setCallId(callIdStr.trim());
        if (delimiter != -1) {
            // Parse parameters if present
            String parameterString = body.substring(delimiter + 1).trim();
            if (!parameterString.isEmpty()) {
                this.parameters = new NameValueList();
                this.parameters.setSeparator(SEMICOLON);
                this.parameters.setQuotedValue();
                this.parameters.setEscaped(false);
                String[] paramArray = parameterString.split(";");
                for (String param : paramArray) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        this.parameters.set(pair[0].trim(), pair[1].trim());
                    }
                }
            }
        }
    }
}

