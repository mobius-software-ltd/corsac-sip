package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;
import java.util.Iterator;
import javax.sip.header.ExtensionHeader;
import gov.nist.javax.sip.header.*;

/**
 * SIP Target-Dialog header implementation.
 */
public class TargetDialog extends ParametersHeader implements ExtensionHeader, TargetDialogHeader {

	  private static final long serialVersionUID = 1L;

	  public CallIdentifier callId;

	  public static final String NAME = SIPHeaderNames.TARGET_DIALOG;

	  private String localTag;
	  private String remoteTag;

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

  // New methods for local and remote tags

  /**
   * Gets the local tag of the Target-Dialog header.
   *
   * @return the local tag value
   */
  public String getLocalTag() {
    return localTag;
  }

  /**
   * Sets the local tag of the Target-Dialog header.
   *
   * @param localTag the local tag value to set
   * @throws ParseException if the provided localTag is invalid
   */
  public void setLocalTag(String localTag) throws ParseException {
    if (localTag == null || localTag.trim().isEmpty()) {
      throw new ParseException("Local tag cannot be null or empty", 0);
    }
    this.localTag = localTag.trim();
  }

  /**
   * Gets the remote tag of the Target-Dialog header.
   *
   * @return the remote tag value
   */
  public String getRemoteTag() {
    return remoteTag;
  }

  /**
   * Sets the remote tag of the Target-Dialog header.
   *
   * @param remoteTag the remote tag value to set
   * @throws ParseException if the provided remoteTag is invalid
   */
  public void setRemoteTag(String remoteTag) throws ParseException {
    if (remoteTag == null || remoteTag.trim().isEmpty()) {
      throw new ParseException("Remote tag cannot be null or empty", 0);
    }
    this.remoteTag = remoteTag.trim();
  }

  @Override
  public StringBuilder encodeBody(StringBuilder retval) {
    if (callId != null) {
      retval.append(callId.encode());
      if (localTag != null) {
        retval.append(";"); // Use semicolon directly
        retval.append(localTag);
      }
      if (remoteTag != null) {
        retval.append(";"); // Use semicolon directly
        retval.append(remoteTag);
      }
      // Encode parameters (if any)
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
    retval.localTag = localTag;
    retval.remoteTag = remoteTag;
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
   * Parses the header string into Call-Id, local tag, remote tag, and parameters.
   *
   * @param body the header string to parse
   * @throws ParseException if the header string cannot be parsed
   */
  public void decodeBody(String body) throws ParseException {
	    int localTagIndex = body.indexOf(';');
	    int remoteTagIndex = body.indexOf(';');

	    if (localTagIndex != -1 && remoteTagIndex != -1) {
	      // If both semicolons are present, ensure they're not next to each other
	      if (localTagIndex == remoteTagIndex + 1) {
	        throw new ParseException("Invalid Target-Dialog header format", 0);
	      }
	    }

	    int delimiter = localTagIndex != -1 ? localTagIndex : (remoteTagIndex != -1 ? remoteTagIndex : -1);

	    String callIdStr = (delimiter != -1) ? body.substring(0, delimiter) : body;
	    setCallId(callIdStr.trim());

	    if (localTagIndex != -1) {
	      localTag = body.substring(localTagIndex + 1).trim(); // Skip leading semicolon
	      int nextSemicolon = body.indexOf(';', localTagIndex + 1);
	      if (nextSemicolon != -1 && nextSemicolon < remoteTagIndex) {
	        throw new ParseException("Invalid local tag format in Target-Dialog header", 0);
	      }
	    }

	    if (remoteTagIndex != -1) {
	      remoteTag = body.substring(remoteTagIndex + 1).trim(); // Skip leading semicolon
	    }
  }
}
