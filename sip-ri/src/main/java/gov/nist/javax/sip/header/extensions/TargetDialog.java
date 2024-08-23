package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import javax.sip.header.ExtensionHeader;
import gov.nist.javax.sip.header.*;

/**
 * SIP Target-Dialog header implementation.
 */
public class TargetDialog extends ParametersHeader implements ExtensionHeader, TargetDialogHeader {

    private static final long serialVersionUID = 1L;
    
    public CallIdentifier callIdentifier;
    public String callId;

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
     * @param callId CallIdentifier to set
     */
    public TargetDialog(String callId) throws IllegalArgumentException {
        super(NAME);
        this.callIdentifier = new CallIdentifier(callId);;
    }

    /**
     * Encode the body part of this header (i.e. leave out the hdrName).
     * @return String encoded body part of the header.
     */
    public StringBuilder encodeBody(StringBuilder retval) {
        if (callId == null)
            return retval;
        else {
            retval.append(callId);
            if (!parameters.isEmpty()) {
                retval.append(SEMICOLON);
                parameters.encode(retval);
            }
            return retval;
        }
    }
    /**
     * get the CallId field. This does the same thing as encodeBody
     *
     * @return String the encoded body part of the
     */
    public String getCallId() {
        return callId;
    }

    public CallIdentifier getCallIdentifer() {
        return callIdentifier;
    }

    /**
     * set the CallId field
     * @param cid String to set. This is the body part of the Call-Id
     *  header. It must have the form localId@host or localId.
     * @throws IllegalArgumentException if cid is null, not a token, or is
     * not a token@token.
     */
    public void setCallId(String cid) {
        callId = cid;
    }

    /**
     * Set the callIdentifier member.
     * @param cid CallIdentifier to set (localId@host).
     */
    public void setCallIdentifier(CallIdentifier cid) {
        callIdentifier = cid;
    }

    /**
     * Gets the local-tag parameter from the address param list.
     * @return tag field
     */
    public String getLocalTag() {
        if (parameters == null)
            return null;
        return getParameter(ParameterNames.LOCAL_TAG);
    }
    /**
     * Sets the local tag of the Target-Dialog header.
     *
     * @param t the local tag value to set
     * @throws ParseException if the provided localTag is invalid
     */
    public void setLocalTag(String t) throws ParseException {
    	  if (t == null)
              throw new NullPointerException("null tag ");
          else if (t.trim().equals(""))
              throw new ParseException("bad tag", 0);
          this.setParameter(ParameterNames.LOCAL_TAG, t);
    }
    /** Boolean function
     * @return true if the Tag exist
     */
    public boolean hasLocalTag() {
        return hasParameter(ParameterNames.LOCAL_TAG);
    }

    /** remove Tag member
     */
    public void removeLocalTag() {
        parameters.delete(ParameterNames.LOCAL_TAG);
        
    }
    /**
     * Gets the remote tag of the Target-Dialog header.
     *
     * @return the remote tag value
     */
    public String getRemoteTag() {
    	 if (parameters == null)
             return null;
         return getParameter(ParameterNames.REMOTE_TAG);
    }
    /**
     * Sets the remote tag of the Target-Dialog header.
     *
     * @param t the remote tag value to set
     * @throws ParseException if the provided remoteTag is invalid
     */
    public void setRemoteTag(String t) throws ParseException {
    	  if (t == null)
              throw new NullPointerException("null tag ");
          else if (t.trim().equals(""))
              throw new ParseException("bad tag", 0);
          this.setParameter(ParameterNames.REMOTE_TAG, t);
    }
    /** Boolean function
     * @return true if the Tag exist
     */
    public boolean hasRemoteTag() {
        return hasParameter(ParameterNames.REMOTE_TAG);
    }

    /** remove Tag member
     */
    public void removeRemoteTag() {
        parameters.delete(ParameterNames.REMOTE_TAG);      
    }

    @Override
    public void setValue(String value) throws ParseException {
        decodeBody(value);
    }
    
    /**
     * Parses the header string into Call-Id, local tag, remote tag, and parameters.
     *
     * @param body the header string to parse
     */
    public void decodeBody(String body) {
        try {
            // Split the header by semicolons to extract each parameter
            String[] params = body.split(";");

            // Extract the call ID from the first parameter
            String callIdParam = params[0].trim();
            int equalIndex = callIdParam.indexOf('=');
            if (equalIndex != -1) {
                setCallId(callIdParam.substring(equalIndex + 1).trim());
            } else {
                throw new ParseException("Invalid Target-Dialog header format: missing Call-Id", 0);
            }

            // Extract local and remote tags from subsequent parameters
            for (int i = 1; i < params.length; i++) {
                String param = params[i].trim();
                equalIndex = param.indexOf('=');
                if (equalIndex != -1) {
                    String paramName = param.substring(0, equalIndex).trim();
                    String paramValue = param.substring(equalIndex + 1).trim();
                    if (paramName.equalsIgnoreCase(ParameterNames.LOCAL_TAG)) {
                        setLocalTag(paramValue);
                    } else if (paramName.equalsIgnoreCase(ParameterNames.REMOTE_TAG)) {
                        setRemoteTag(paramValue);
                    }
                }
            }
        } catch (ParseException e) {
            // Handle the exception gracefully, e.g., log an error message
            System.err.println("Error parsing Target-Dialog header: " + e.getMessage());
        }
    }
}