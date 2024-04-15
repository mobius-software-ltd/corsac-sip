package gov.nist.javax.sip.header.extensions;

import java.text.ParseException;

import javax.sip.header.Header;
import javax.sip.header.Parameters;

public interface TargetDialogHeader extends Parameters, Header{
	
	 /**
     * Sets the Call-Id of the TargetDialogHeader. The CallId parameter uniquely
     * identifies a serious of messages within a dialogue.
     *  @author ValeriiaMukha
     *
     * @param callId - the string value of the Call-Id of this TargetDialogHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the callId value.
     */
    public void setCallId(String callId) throws ParseException;

    /**
     * Returns the Call-Id of TargetDialogHeader. The CallId parameter uniquely
     * identifies a series of messages within a dialogue.
     *
     * @return the String value of the Call-Id of this InReplyToHeader
     */
    public String getCallId();
	
    /**
     * @param tag - the new tag of the TargetDialogHeader
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the Tag value.
     */
    public void setLocalTag(String localTag) throws ParseException;
    public void setRemoteTag(String remoteTag) throws ParseException;
    
    /**
     * @return the tag parameter of the TargetDialogHeader.
     */

    public String getLocalTag();
    public String getRemoteTag(); 
	
    /**
     * Name of TargetDialogHeader
     */
	public final static String NAME = "Target-Dialog";
	
}
