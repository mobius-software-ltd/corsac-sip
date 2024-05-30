/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 Untied States Code Section 105, works of NIST
* employees are not subject to copyright protection in the United States
* and are considered to be in the public domain.  As a result, a formal
* license is not needed to use the software.
*
* This software is provided by NIST as a service and is expressly
* provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
* Permission to use this software is contingent upon your acceptance
* of the terms of this agreement.
*
*/
/*******************************************************************************
* Product of NIST/ITL Advanced Networking Technologies Division (ANTD).        *
*******************************************************************************/
package gov.nist.javax.sip.header;
import java.text.ParseException;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.header.AcceptEncodingHeader;
import javax.sip.header.AcceptHeader;
import javax.sip.header.AcceptLanguageHeader;
import javax.sip.header.AlertInfoHeader;
import javax.sip.header.AllowEventsHeader;
import javax.sip.header.AllowHeader;
import javax.sip.header.AuthenticationInfoHeader;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.CallInfoHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentDispositionHeader;
import javax.sip.header.ContentEncodingHeader;
import javax.sip.header.ContentLanguageHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.DateHeader;
import javax.sip.header.ErrorInfoHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.ExtensionHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.InReplyToHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.MimeVersionHeader;
import javax.sip.header.MinExpiresHeader;
import javax.sip.header.OrganizationHeader;
import javax.sip.header.PriorityHeader;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ProxyRequireHeader;
import javax.sip.header.RAckHeader;
import javax.sip.header.RSeqHeader;
import javax.sip.header.ReasonHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ReferToHeader;
import javax.sip.header.ReplyToHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RetryAfterHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.SIPETagHeader;
import javax.sip.header.SIPIfMatchHeader;
import javax.sip.header.ServerHeader;
import javax.sip.header.SubjectHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.header.TimeStampHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UnsupportedHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.header.WarningHeader;

import gov.nist.javax.sip.address.GenericURI;
import gov.nist.javax.sip.header.extensions.AcceptResourcePriority;
import gov.nist.javax.sip.header.extensions.AcceptResourcePriorityHeader;
import gov.nist.javax.sip.header.extensions.Diversion;
import gov.nist.javax.sip.header.extensions.DiversionHeader;
// extension headers - pmusgrave
import gov.nist.javax.sip.header.extensions.Join;
import gov.nist.javax.sip.header.extensions.JoinHeader;
import gov.nist.javax.sip.header.extensions.MinSE;
import gov.nist.javax.sip.header.extensions.References;
import gov.nist.javax.sip.header.extensions.ReferencesHeader;
import gov.nist.javax.sip.header.extensions.ReferredBy;
import gov.nist.javax.sip.header.extensions.ReferredByHeader;
import gov.nist.javax.sip.header.extensions.Replaces;
import gov.nist.javax.sip.header.extensions.ReplacesHeader;
import gov.nist.javax.sip.header.extensions.ResourcePriority;
import gov.nist.javax.sip.header.extensions.ResourcePriorityHeader;
import gov.nist.javax.sip.header.extensions.SessionExpires;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import gov.nist.javax.sip.header.extensions.TargetDialog;
import gov.nist.javax.sip.header.extensions.TargetDialogHeader;
/* IMS headers - issued by Miguel Freitas */
import gov.nist.javax.sip.header.ims.PAccessNetworkInfo;
import gov.nist.javax.sip.header.ims.PAccessNetworkInfoHeader;
import gov.nist.javax.sip.header.ims.PAssertedIdentity;
import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import gov.nist.javax.sip.header.ims.PAssertedService;
import gov.nist.javax.sip.header.ims.PAssertedServiceHeader;
import gov.nist.javax.sip.header.ims.PAssociatedURI;
import gov.nist.javax.sip.header.ims.PAssociatedURIHeader;
import gov.nist.javax.sip.header.ims.PCalledPartyID;
import gov.nist.javax.sip.header.ims.PCalledPartyIDHeader;
import gov.nist.javax.sip.header.ims.PChargingFunctionAddresses;
import gov.nist.javax.sip.header.ims.PChargingFunctionAddressesHeader;
import gov.nist.javax.sip.header.ims.PChargingVector;
import gov.nist.javax.sip.header.ims.PChargingVectorHeader;
import gov.nist.javax.sip.header.ims.PMediaAuthorization;
import gov.nist.javax.sip.header.ims.PMediaAuthorizationHeader;
import gov.nist.javax.sip.header.ims.PPreferredIdentity;
import gov.nist.javax.sip.header.ims.PPreferredIdentityHeader;
import gov.nist.javax.sip.header.ims.PPreferredService;
import gov.nist.javax.sip.header.ims.PPreferredServiceHeader;
import gov.nist.javax.sip.header.ims.PProfileKey;
import gov.nist.javax.sip.header.ims.PProfileKeyHeader;
import gov.nist.javax.sip.header.ims.PServedUser;
import gov.nist.javax.sip.header.ims.PServedUserHeader;
import gov.nist.javax.sip.header.ims.PUserDatabase;
import gov.nist.javax.sip.header.ims.PUserDatabaseHeader;
import gov.nist.javax.sip.header.ims.PVisitedNetworkID;
import gov.nist.javax.sip.header.ims.PVisitedNetworkIDHeader;
import gov.nist.javax.sip.header.ims.Path;
import gov.nist.javax.sip.header.ims.PathHeader;
import gov.nist.javax.sip.header.ims.Privacy;
import gov.nist.javax.sip.header.ims.PrivacyHeader;
import gov.nist.javax.sip.header.ims.SecurityClient;
import gov.nist.javax.sip.header.ims.SecurityClientHeader;
import gov.nist.javax.sip.header.ims.SecurityServer;
import gov.nist.javax.sip.header.ims.SecurityServerHeader;
import gov.nist.javax.sip.header.ims.SecurityVerify;
import gov.nist.javax.sip.header.ims.SecurityVerifyHeader;
import gov.nist.javax.sip.header.ims.ServiceRoute;
import gov.nist.javax.sip.header.ims.ServiceRouteHeader;
import gov.nist.javax.sip.parser.RequestLineParser;
import gov.nist.javax.sip.parser.StatusLineParser;
import gov.nist.javax.sip.parser.StringMsgParser;

/*
* This file contains enhancements contributed by Alexandre Silva Santos
* (PT-Inovacao) and Miguel Freitas
*/

/** Implementation of the JAIN SIP  HeaderFactory
*
* @version 1.2 $Revision: 1.23 $ $Date: 2010-05-06 14:07:51 $
* @since 1.1
*
*@author M. Ranganathan   <br/>
*@author Olivier Deruelle <br/>
*
*
*/
public class HeaderFactoryImpl implements HeaderFactoryExt {

    /**
     * Determines whether or not we should tolerate and strip address scope
     * zones from IPv6 addresses. Address scope zones are sometimes returned
     * at the end of IPv6 addresses generated by InetAddress.getHostAddress().
     * They are however not part of the SIP semantics so basically this method
     * determines whether or not the parser should be stripping them (as
     * opposed simply being blunt and throwing an exception).
     */
    private boolean stripAddressScopeZones = false;

    /**
     * Set pretty encoding on / off.
     * This splits up via headers into multiple lines for readability ( better for
     * debugging ).
     *
     */
    public void setPrettyEncoding(boolean flag) {
        SIPHeaderList.setPrettyEncode(flag);
    }

    /**
    * Creates a new AcceptEncodingHeader based on the newly supplied encoding
    * value.
    *
    * @param encoding - the new string containing the encoding value.
    * @throws ParseException which signals that an error has been reached
    * unexpectedly while parsing the encoding value.
    * @return the newly created AcceptEncodingHeader object.
    */
    public AcceptEncodingHeader createAcceptEncodingHeader(String encoding)
        throws ParseException {
        if (encoding == null)
            throw new NullPointerException("the encoding parameter is null");
        AcceptEncoding acceptEncoding = new AcceptEncoding();
        acceptEncoding.setEncoding(encoding);
        return acceptEncoding;
    }

    /**
     * Creates a new AcceptHeader based on the newly supplied contentType and
     * contentSubType values.
     *
     * @param contentType The new string content type value.
     * @param contentSubType The new string content sub-type value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the content type or content subtype value.
     * @return the newly created AcceptHeader object.
     */
    public AcceptHeader createAcceptHeader(
        String contentType,
        String contentSubType)
        throws ParseException {
        if (contentType == null || contentSubType == null)
            throw new NullPointerException("contentType or subtype is null ");
        Accept accept = new Accept();
        accept.setContentType(contentType);
        accept.setContentSubType(contentSubType);

        return accept;
    }

    /**
     * Creates a new AcceptLanguageHeader based on the newly supplied
     * language value.
     *
     * @param language - the new Locale value of the language
     * @return the newly created AcceptLanguageHeader object.
     */
    public AcceptLanguageHeader createAcceptLanguageHeader(Locale language) {
        if (language == null)
            throw new NullPointerException("null arg");
        AcceptLanguage acceptLanguage = new AcceptLanguage();
        acceptLanguage.setAcceptLanguage(language);

        return acceptLanguage;
    }

    /**
     * Creates a new AlertInfoHeader based on the newly supplied alertInfo value.
     *
     * @param alertInfo - the new URI value of the alertInfo
     * @return the newly created AlertInfoHeader object.
     * @since v1.1
     */
    public AlertInfoHeader createAlertInfoHeader(URI alertInfo) {
        if (alertInfo == null)
            throw new NullPointerException("null arg alertInfo");
        AlertInfo a = new AlertInfo();
        a.setAlertInfo(alertInfo);
        return a;
    }

    /**
     * Creates a new AllowEventsHeader based on the newly supplied event type
     * value.
     *
     * @param eventType - the new string containing the eventType value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the eventType value.
     * @return the newly created AllowEventsHeader object.
     * @since v1.1
     */
    public AllowEventsHeader createAllowEventsHeader(String eventType)
        throws ParseException {
        if (eventType == null)
            throw new NullPointerException("null arg eventType");
        AllowEvents allowEvents = new AllowEvents();
        allowEvents.setEventType(eventType);
        return allowEvents;
    }

    /**
     * Creates a new AllowHeader based on the newly supplied method value.
     *
     * @param method - the new string containing the method value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the method value.
     * @return the newly created AllowHeader object.
     */
    public AllowHeader createAllowHeader(String method) throws ParseException {
        if (method == null)
            throw new NullPointerException("null arg method");
        Allow allow = new Allow();
        allow.setMethod(method);

        return allow;
    }

    /**
     * Creates a new AuthenticationInfoHeader based on the newly supplied
     * response value.
     *
     * @param response - the new string value of the response.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the response value.
     * @return the newly created AuthenticationInfoHeader object.
     * @since v1.1
     */
    public AuthenticationInfoHeader createAuthenticationInfoHeader(String response)
        throws ParseException {
        if (response == null)
            throw new NullPointerException("null arg response");
        AuthenticationInfo auth = new AuthenticationInfo();
        auth.setResponse(response);

        return auth;
    }

    /**
     * Creates a new AuthorizationHeader based on the newly supplied
     * scheme value.
     *
     * @param scheme - the new string value of the scheme.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the scheme value.
     * @return the newly created AuthorizationHeader object.
     */
    public AuthorizationHeader createAuthorizationHeader(String scheme)
        throws ParseException {
        if (scheme == null)
            throw new NullPointerException("null arg scheme ");
        Authorization auth = new Authorization();
        auth.setScheme(scheme);

        return auth;
    }

    /**
     * Creates a new CSeqHeader based on the newly supplied sequence number and
     * method values.
     *
     * @param sequenceNumber - the new integer value of the sequence number.
     * @param method - the new string value of the method.
     * @throws InvalidArgumentException if supplied sequence number is less
     * than zero.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the method value.
     * @return the newly created CSeqHeader object.
     */
    public CSeqHeader createCSeqHeader( long sequenceNumber, String method)
        throws ParseException, InvalidArgumentException {
        if (sequenceNumber < 0)
            throw new InvalidArgumentException("bad arg " + sequenceNumber);
        if (method == null)
            throw new NullPointerException("null arg method");
        CSeq cseq = new CSeq();
        cseq.setMethod(method);
        cseq.setSeqNumber(sequenceNumber);

        return cseq;
    }

    /**
     * For backwards compatibility, also accept int
     * @deprecated
     */
    public CSeqHeader createCSeqHeader( int sequenceNumber, String method)
        throws ParseException, InvalidArgumentException {
        return this.createCSeqHeader( (long) sequenceNumber, method );
    }

    /**
     * Creates a new CallIdHeader based on the newly supplied callId value.
     *
     * @param callId - the new string value of the call-id.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the callId value.
     * @return the newly created CallIdHeader object.
     */
    public CallIdHeader createCallIdHeader(String callId)
        throws ParseException {
        if (callId == null)
            throw new NullPointerException("null arg callId");
        CallID c = new CallID();
        c.setCallId(callId);
        return c;
    }

    /**
     * Creates a new CallInfoHeader based on the newly supplied callInfo value.
     *
     * @param callInfo The new string value of the callInfo.
     * @return the newly created CallInfoHeader object.
     */
    public CallInfoHeader createCallInfoHeader(URI callInfo) {
        if (callInfo == null)
            throw new NullPointerException("null arg callInfo");

        CallInfo c = new CallInfo();
        c.setInfo(callInfo);
        return c;
    }

    /**
     * Creates a new ContactHeader based on the newly supplied address value.
     *
     * @param address - the new Address value of the address.
     * @return the newly created ContactHeader object.
     */
    public ContactHeader createContactHeader(Address address) {
        if (address == null)
            throw new NullPointerException("null arg address");
        Contact contact = new Contact();
        contact.setAddress(address);

        return contact;
    }

    /**
    * Creates a new wildcard ContactHeader. This is used in Register requests
    * to indicate to the server that it should remove all locations the
    * at which the user is currently available. This implies that the
    * following conditions are met:
    * <ul>
    * <li><code>ContactHeader.getAddress.getAddress.getUserInfo() == *;</code>
    * <li><code>ContactHeader.getAddress.getAddress.isWildCard() == true;</code>
    * <li><code>ContactHeader.getExpires() == 0;</code>
    * </ul>
    *
    * @return the newly created wildcard ContactHeader.
    */
    public ContactHeader createContactHeader() {
        Contact contact = new Contact();
        contact.setWildCardFlag(true);
        contact.setExpires(0);

        return contact;
    }

    /**
     * Creates a new ContentDispositionHeader based on the newly supplied
     * contentDisposition value.
     *
     * @param contentDisposition - the new string value of the contentDisposition.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the contentDisposition value.
     * @return the newly created ContentDispositionHeader object.
     * @since v1.1
     */
    public ContentDispositionHeader createContentDispositionHeader(String contentDisposition)
        throws ParseException {
        if (contentDisposition == null)
            throw new NullPointerException("null arg contentDisposition");
        ContentDisposition c = new ContentDisposition();
        c.setDispositionType(contentDisposition);

        return c;
    }

    /**
    * Creates a new ContentEncodingHeader based on the newly supplied encoding
    * value.
    *
    * @param encoding - the new string containing the encoding value.
    * @throws ParseException which signals that an error has been reached
    * unexpectedly while parsing the encoding value.
    * @return the newly created ContentEncodingHeader object.
    */
    public ContentEncodingHeader createContentEncodingHeader(String encoding)
        throws ParseException {
        if (encoding == null)
            throw new NullPointerException("null encoding");
        ContentEncoding c = new ContentEncoding();
        c.setEncoding(encoding);

        return c;
    }

    /**
     * Creates a new ContentLanguageHeader based on the newly supplied
     * contentLanguage value.
     *
     * @param contentLanguage - the new Locale value of the contentLanguage.
     * @return the newly created ContentLanguageHeader object.
     * @since v1.1
     */
    public ContentLanguageHeader createContentLanguageHeader(Locale contentLanguage) {
        if (contentLanguage == null)
            throw new NullPointerException("null arg contentLanguage");
        ContentLanguage c = new ContentLanguage();
        c.setContentLanguage(contentLanguage);

        return c;
    }

    /**
     * Creates a new CSeqHeader based on the newly supplied contentLength value.
     *
     * @param contentLength - the new integer value of the contentLength.
     * @throws InvalidArgumentException if supplied contentLength is less
     * than zero.
     * @return the newly created ContentLengthHeader object.
     */
    public ContentLengthHeader createContentLengthHeader(int contentLength)
        throws InvalidArgumentException {
        if (contentLength < 0)
            throw new InvalidArgumentException("bad contentLength");
        ContentLength c = new ContentLength();
        c.setContentLength(contentLength);

        return c;
    }

    /**
     * Creates a new ContentTypeHeader based on the newly supplied contentType and
     * contentSubType values.
     *
     * @param contentType - the new string content type value.
     * @param contentSubType - the new string content sub-type value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the content type or content subtype value.
     * @return the newly created ContentTypeHeader object.
     */
    public ContentTypeHeader createContentTypeHeader(
        String contentType,
        String contentSubType)
        throws ParseException {
        if (contentType == null || contentSubType == null)
            throw new NullPointerException("null contentType or subType");
        ContentType c = new ContentType();
        c.setContentType(contentType);
        c.setContentSubType(contentSubType);
        return c;
    }

    /**
    * Creates a new DateHeader based on the newly supplied date value.
    *
    * @param date - the new Calender value of the date.
    * @return the newly created DateHeader object.
    */
    public DateHeader createDateHeader(Calendar date) {
        SIPDateHeader d = new SIPDateHeader();
        if (date == null)
            throw new NullPointerException("null date");
        d.setDate(date);

        return d;
    }

    /**
     * Creates a new EventHeader based on the newly supplied eventType value.
     *
     * @param eventType - the new string value of the eventType.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the eventType value.
     * @return the newly created EventHeader object.
     * @since v1.1
     */
    public EventHeader createEventHeader(String eventType)
        throws ParseException {
        if (eventType == null)
            throw new NullPointerException("null eventType");
        Event event = new Event();
        event.setEventType(eventType);

        return event;
    }

    /**
     * Creates a new ExpiresHeader based on the newly supplied expires value.
     *
     * @param expires - the new integer value of the expires.
     * @throws InvalidArgumentException if supplied expires is less
     * than zero.
     * @return the newly created ExpiresHeader object.
     */
    public ExpiresHeader createExpiresHeader(int expires)
        throws InvalidArgumentException {
        if (expires < 0)
            throw new InvalidArgumentException("bad value " + expires);
        Expires e = new Expires();
        e.setExpires(expires);

        return e;
    }

    /**
     * Creates a new ExtensionHeader based on the newly supplied name and
     * value values.
     *
     * @param name - the new string name of the ExtensionHeader value.
     * @param value - the new string value of the ExtensionHeader.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the name or value values.
     * @return the newly created ExtensionHeader object.
     */
    public javax.sip.header.ExtensionHeader createExtensionHeader(
        String name,
        String value)
        throws ParseException {
        if (name == null)
            throw new NullPointerException("bad name");

        gov.nist.javax.sip.header.ExtensionHeaderImpl ext =
            new gov.nist.javax.sip.header.ExtensionHeaderImpl();
        ext.setName(name);
        ext.setValue(value);

        return ext;
    }

    /**
     * Creates a new FromHeader based on the newly supplied address and
     * tag values.
     *
     * @param address - the new Address object of the address.
     * @param tag - the new string value of the tag.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the tag value.
     * @return the newly created FromHeader object.
     */
    public FromHeader createFromHeader(Address address, String tag)
        throws ParseException {
        if (address == null)
            throw new NullPointerException("null address arg");
        From from = new From();
        from.setAddress(address);
        if (tag != null)
            from.setTag(tag);

        return from;
    }

    /**
     * Creates a new InReplyToHeader based on the newly supplied callId
     * value.
     *
     * @param callId - the new string containing the callId value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the callId value.
     * @return the newly created InReplyToHeader object.
     * @since v1.1
     */
    public InReplyToHeader createInReplyToHeader(String callId)
        throws ParseException {
        if (callId == null)
            throw new NullPointerException("null callId arg");
        InReplyTo inReplyTo = new InReplyTo();
        inReplyTo.setCallId(callId);

        return inReplyTo;
    }
    /**
    * Creates a new MaxForwardsHeader based on the newly
    * supplied maxForwards value.
    *
    * @param maxForwards The new integer value of the maxForwards.
    * @throws InvalidArgumentException if supplied maxForwards is less
    * than zero or greater than 255.
    * @return the newly created MaxForwardsHeader object.
    */
    public MaxForwardsHeader createMaxForwardsHeader(int maxForwards)
        throws InvalidArgumentException {
        if (maxForwards < 0 || maxForwards > 255)
            throw new InvalidArgumentException(
                "bad maxForwards arg " + maxForwards);
        MaxForwards m = new MaxForwards();
        m.setMaxForwards(maxForwards);

        return m;
    }

    /**
     * Creates a new MimeVersionHeader based on the newly
     * supplied mimeVersion value.
     *
     * @param majorVersion - the new integer value of the majorVersion.
     * @param minorVersion - the new integer value of the minorVersion.
     * @throws InvalidArgumentException if supplied mimeVersion is less
     * than zero.
     * @return the newly created MimeVersionHeader object.
     * @since v1.1
     */
    public MimeVersionHeader createMimeVersionHeader(
        int majorVersion,
        int minorVersion)
        throws InvalidArgumentException {
        if (majorVersion < 0 || minorVersion < 0)
            throw new javax.sip.InvalidArgumentException(
                "bad major/minor version");
        MimeVersion m = new MimeVersion();
        m.setMajorVersion(majorVersion);
        m.setMinorVersion(minorVersion);

        return m;
    }

    /**
     * Creates a new MinExpiresHeader based on the newly supplied minExpires value.
     *
     * @param minExpires - the new integer value of the minExpires.
     * @throws InvalidArgumentException if supplied minExpires is less
     * than zero.
     * @return the newly created MinExpiresHeader object.
     * @since v1.1
     */
    public MinExpiresHeader createMinExpiresHeader(int minExpires)
        throws InvalidArgumentException {
        if (minExpires < 0)
            throw new InvalidArgumentException("bad minExpires " + minExpires);
        MinExpires min = new MinExpires();
        min.setExpires(minExpires);

        return min;
    }

    /**
     * Creates a new MinSEHeader based on the newly supplied expires value.
     *
     * @param expires - the new integer value of the expires.
     * @throws InvalidArgumentException if supplied expires is less
     * than zero.
     * @return the newly created ExpiresHeader object.
     *
     * TODO: Once interfaces are in javax, change the type to MinSEHeader
     * and add to HeaderFactory. - pmusgrave
     *
     * pmusgrave
     */
    public ExtensionHeader createMinSEHeader(int expires)
        throws InvalidArgumentException {
        if (expires < 0)
            throw new InvalidArgumentException("bad value " + expires);
        MinSE e = new MinSE();
        e.setExpires(expires);

        return e;
    }

    /**
     * Creates a new OrganizationHeader based on the newly supplied
     * organization value.
     *
     * @param organization - the new string value of the organization.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the organization value.
     * @return the newly created OrganizationHeader object.
     */
    public OrganizationHeader createOrganizationHeader(String organization)
        throws ParseException {
        if (organization == null)
            throw new NullPointerException("bad organization arg");
        Organization o = new Organization();
        o.setOrganization(organization);

        return o;
    }

    /**
     * Creates a new PriorityHeader based on the newly supplied priority value.
     *
     * @param priority - the new string value of the priority.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the priority value.
     * @return the newly created PriorityHeader object.
     */
    public PriorityHeader createPriorityHeader(String priority)
        throws ParseException {
        if (priority == null)
            throw new NullPointerException("bad priority arg");
        Priority p = new Priority();
        p.setPriority(priority);

        return p;
    }

    /**
     * Creates a new ProxyAuthenticateHeader based on the newly supplied
     * scheme value.
     *
     * @param scheme - the new string value of the scheme.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the scheme value.
     * @return the newly created ProxyAuthenticateHeader object.
     */
    public ProxyAuthenticateHeader createProxyAuthenticateHeader(String scheme)
        throws ParseException {
        if (scheme == null)
            throw new NullPointerException("bad scheme arg");
        ProxyAuthenticate p = new ProxyAuthenticate();
        p.setScheme(scheme);

        return p;
    }

    /**
     * Creates a new ProxyAuthorizationHeader based on the newly supplied
     * scheme value.
     *
     * @param scheme - the new string value of the scheme.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the scheme value.
     * @return the newly created ProxyAuthorizationHeader object.
     */
    public ProxyAuthorizationHeader createProxyAuthorizationHeader(String scheme)
        throws ParseException {
        if (scheme == null)
            throw new NullPointerException("bad scheme arg");
        ProxyAuthorization p = new ProxyAuthorization();
        p.setScheme(scheme);

        return p;
    }

    /**
     * Creates a new ProxyRequireHeader based on the newly supplied optionTag
     * value.
     *
     * @param optionTag - the new string OptionTag value.
     * @return the newly created ProxyRequireHeader object.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the optionTag value.
     */
    public ProxyRequireHeader createProxyRequireHeader(String optionTag)
        throws ParseException {
        if (optionTag == null)
            throw new NullPointerException("bad optionTag arg");
        ProxyRequire p = new ProxyRequire();
        p.setOptionTag(optionTag);

        return p;
    }

    /**
     * Creates a new RAckHeader based on the newly supplied rSeqNumber,
     * cSeqNumber and method values.
     *
     * @param rSeqNumber - the new integer value of the rSeqNumber.
     * @param cSeqNumber - the new integer value of the cSeqNumber.
     * @param method - the new string value of the method.
     * @throws InvalidArgumentException if supplied rSeqNumber or cSeqNumber is
     * less than zero or greater than than 2**31-1.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the method value.
     * @return the newly created RAckHeader object.
     * @since v1.1
     */
    public RAckHeader createRAckHeader(
        long rSeqNumber,
        long cSeqNumber,
        String method)
        throws InvalidArgumentException, ParseException {
        if (method == null)
            throw new NullPointerException("Bad method");
        if (cSeqNumber < 0 || rSeqNumber < 0)
            throw new InvalidArgumentException("bad cseq/rseq arg");
        RAck rack = new RAck();
        rack.setMethod(method);
        rack.setCSequenceNumber(cSeqNumber);
        rack.setRSequenceNumber(rSeqNumber);

        return rack;
    }

    /**
     * @deprecated
     * @see javax.sip.header.HeaderFactory#createRAckHeader(int, int, java.lang.String)
     */
    public RAckHeader createRAckHeader(int rSeqNumber, int cSeqNumber, String method) throws InvalidArgumentException, ParseException {

        return createRAckHeader((long)rSeqNumber, (long)cSeqNumber, method);
    }


    /**
     * @deprecated
     * @see javax.sip.header.HeaderFactory#createRSeqHeader(int)
     */
    public RSeqHeader createRSeqHeader(int sequenceNumber) throws InvalidArgumentException {

        return createRSeqHeader((long) sequenceNumber) ;
    }

    /**
     * Creates a new RSeqHeader based on the newly supplied sequenceNumber value.
     *
     * @param sequenceNumber - the new integer value of the sequenceNumber.
     * @throws InvalidArgumentException if supplied sequenceNumber is
     * less than zero or greater than than 2**31-1.
     * @return the newly created RSeqHeader object.
     * @since v1.1
     */
    public RSeqHeader createRSeqHeader(long sequenceNumber)
        throws InvalidArgumentException {
        if (sequenceNumber < 0)
            throw new InvalidArgumentException(
                "invalid sequenceNumber arg " + sequenceNumber);
        RSeq rseq = new RSeq();
        rseq.setSeqNumber(sequenceNumber);

        return rseq;
    }

    /**
     * Creates a new ReasonHeader based on the newly supplied reason value.
     *
     * @param protocol - the new string value of the protocol.
     * @param cause - the new integer value of the cause.
     * @param text - the new string value of the text.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the protocol, cause or text value.
     * @return the newly created ReasonHeader object.
     * @since v1.1
     */
    public ReasonHeader createReasonHeader(
        String protocol,
        int cause,
        String text)
        throws InvalidArgumentException, ParseException {
        if (protocol == null)
            throw new NullPointerException("bad protocol arg");
        if (cause < 0)
            throw new InvalidArgumentException("bad cause");
        Reason reason = new Reason();
        reason.setProtocol(protocol);
        reason.setCause(cause);
        reason.setText(text);

        return reason;
    }

    /**
    * Creates a new RecordRouteHeader based on the newly supplied address value.
    *
    * @param address - the new Address object of the address.
    * @return the newly created RecordRouteHeader object.
    */
    public RecordRouteHeader createRecordRouteHeader(Address address) {
        if ( address == null) throw new NullPointerException("Null argument!");
        RecordRoute recordRoute = new RecordRoute();
        recordRoute.setAddress(address);

        return recordRoute;
    }

    /**
    * Creates a new ReplyToHeader based on the newly supplied address value.
    *
    * @param address - the new Address object of the address.
    * @return the newly created ReplyToHeader object.
    * @since v1.1
    */
    public ReplyToHeader createReplyToHeader(Address address) {
        if (address == null)
            throw new NullPointerException("null address");
        ReplyTo replyTo = new ReplyTo();
        replyTo.setAddress(address);

        return replyTo;
    }

    /**
     * Creates a new RequireHeader based on the newly supplied optionTag
     * value.
     *
     * @param optionTag - the new string value containing the optionTag value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the List of optionTag value.
     * @return the newly created RequireHeader object.
     */
    public RequireHeader createRequireHeader(String optionTag)
        throws ParseException {
        if (optionTag == null)
            throw new NullPointerException("null optionTag");
        Require require = new Require();
        require.setOptionTag(optionTag);

        return require;
    }

    /**
     * Creates a new RetryAfterHeader based on the newly supplied retryAfter
     * value.
     *
     * @param retryAfter - the new integer value of the retryAfter.
     * @throws InvalidArgumentException if supplied retryAfter is less
     * than zero.
     * @return the newly created RetryAfterHeader object.
     */
    public RetryAfterHeader createRetryAfterHeader(int retryAfter)
        throws InvalidArgumentException {
        if (retryAfter < 0)
            throw new InvalidArgumentException("bad retryAfter arg");
        RetryAfter r = new RetryAfter();
        r.setRetryAfter(retryAfter);

        return r;
    }

    /**
     * Creates a new RouteHeader based on the newly supplied address value.
     *
     * @param address - the new Address object of the address.
     * @return the newly created RouteHeader object.
     */
    public RouteHeader createRouteHeader(Address address) {
        if (address == null)
            throw new NullPointerException("null address arg");
        Route route = new Route();
        route.setAddress(address);

        return route;
    }

    /**
     * Creates a new ServerHeader based on the newly supplied product value.
     *
     * @param product - the new list value of the product.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the product value.
     * @return the newly created ServerHeader object.
     */
    public ServerHeader createServerHeader(@SuppressWarnings("rawtypes") List product)
        throws ParseException {
        if (product == null)
            throw new NullPointerException("null productList arg");
        Server server = new Server();
        server.setProduct(product);

        return server;
    }

    /**
     * Creates a new SubjectHeader based on the newly supplied subject value.
     *
     * @param subject - the new string value of the subject.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the subject value.
     * @return the newly created SubjectHeader object.
     */
    public SubjectHeader createSubjectHeader(String subject)
        throws ParseException {
        if (subject == null)
            throw new NullPointerException("null subject arg");
        Subject s = new Subject();
        s.setSubject(subject);

        return s;
    }

    /**
     * Creates a new SubscriptionStateHeader based on the newly supplied
     * subscriptionState value.
     *
     * @param subscriptionState - the new string value of the subscriptionState.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the subscriptionState value.
     * @return the newly created SubscriptionStateHeader object.
     * @since v1.1
     */
    public SubscriptionStateHeader createSubscriptionStateHeader(String subscriptionState)
        throws ParseException {
        if (subscriptionState == null)
            throw new NullPointerException("null subscriptionState arg");
        SubscriptionState s = new SubscriptionState();
        s.setState(subscriptionState);

        return s;
    }

    /**
     * Creates a new SupportedHeader based on the newly supplied optionTag
     * value.
     *
     * @param optionTag - the new string containing the optionTag value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the optionTag value.
     * @return the newly created SupportedHeader object.
     */
    public SupportedHeader createSupportedHeader(String optionTag)
        throws ParseException {
        if (optionTag == null)
            throw new NullPointerException("null optionTag arg");
        Supported supported = new Supported();
        supported.setOptionTag(optionTag);

        return supported;
    }

    /**
     * Creates a new TimeStampHeader based on the newly supplied timeStamp value.
     *
     * @param timeStamp - the new float value of the timeStamp.
     * @throws InvalidArgumentException if supplied timeStamp is less
     * than zero.
     * @return the newly created TimeStampHeader object.
     */
    public TimeStampHeader createTimeStampHeader(float timeStamp)
        throws InvalidArgumentException {
        if (timeStamp < 0)
            throw new IllegalArgumentException("illegal timeStamp");
        TimeStamp t = new TimeStamp();
        t.setTimeStamp(timeStamp);

        return t;
    }

    /**
     * Creates a new ToHeader based on the newly supplied address and
     * tag values.
     *
     * @param address - the new Address object of the address.
     * @param tag - the new string value of the tag.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the tag value.
     * @return the newly created ToHeader object.
     */
    public ToHeader createToHeader(Address address, String tag)
        throws ParseException {
        if (address == null)
            throw new NullPointerException("null address");
        To to = new To();
        to.setAddress(address);
        if (tag != null)
            to.setTag(tag);

        return to;
    }

    /**
     * Creates a new UnsupportedHeader based on the newly supplied optionTag
     * value.
     *
     * @param optionTag - the new string containing the optionTag value.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the List of optionTag value.
     * @return the newly created UnsupportedHeader object.
     */
    public UnsupportedHeader createUnsupportedHeader(String optionTag)
        throws ParseException {
        if (optionTag == null)
            throw new NullPointerException(optionTag);
        Unsupported unsupported = new Unsupported();
        unsupported.setOptionTag(optionTag);

        return unsupported;
    }

    /**
     * Creates a new UserAgentHeader based on the newly supplied product value.
     *
     * @param product - the new list value of the product.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the product value.
     * @return the newly created UserAgentHeader object.
     */
    public UserAgentHeader createUserAgentHeader(@SuppressWarnings("rawtypes") List product)
        throws ParseException {

        if (product == null)
            throw new NullPointerException("null user agent");
        UserAgent userAgent = new UserAgent();
        userAgent.setProduct(product);

        return userAgent;
    }

    /**
     * Creates a new ViaHeader based on the newly supplied uri and branch values.
     *
     * @param host the new host value of uri.
     * @param port the new port value of uri.
     * @param transport the new transport value of uri.
     * @param branch the new string value of the branch.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the branch value.
     * @return the newly created ViaHeader object.
     */
    public ViaHeader createViaHeader(
        String host,
        int port,
        String transport,
        String branch)
        throws ParseException, InvalidArgumentException {
        // This should be changed.
        if (host == null || transport == null)
            throw new NullPointerException("null arg");
        Via via = new Via();
        if (branch != null)
            via.setBranch(branch);

        // for supporting IPv6 addresses
        if(host.indexOf(':') >= 0
            && host.indexOf('[') < 0)
        {
            //strip address scope zones if any
            if(stripAddressScopeZones)
            {
                int zoneStart = host.indexOf('%');
                if(zoneStart != -1)
                    host = host.substring(0, zoneStart);
            }
            host = '[' + host + ']';
        }

        via.setHost(host);
        via.setPort(port);
        via.setTransport(transport);

        return via;
    }

    /**
     * Creates a new WWWAuthenticateHeader based on the newly supplied
     * scheme value.
     *
     * @param scheme - the new string value of the scheme.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the scheme values.
     * @return the newly created WWWAuthenticateHeader object.
     */
    public WWWAuthenticateHeader createWWWAuthenticateHeader(String scheme)
        throws ParseException {
        if (scheme == null)
            throw new NullPointerException("null scheme");
        WWWAuthenticate www = new WWWAuthenticate();
        www.setScheme(scheme);

        return www;
    }

    /**
     * Creates a new WarningHeader based on the newly supplied
     * agent, code and comment values.
     *
     * @param agent - the new string value of the agent.
     * @param code - the new boolean integer of the code.
     * @param comment - the new string value of the comment.
     * @throws ParseException which signals that an error has been reached
     * unexpectedly while parsing the agent or comment values.
     * @throws InvalidArgumentException if an invalid integer code is given for
     * the WarningHeader.
     * @return the newly created WarningHeader object.
     */
    public WarningHeader createWarningHeader(
        String agent,
        int code,
        String comment)
        throws ParseException, InvalidArgumentException {
        if (agent == null)
            throw new NullPointerException("null arg");
        Warning warning = new Warning();
        warning.setAgent(agent);
        warning.setCode(code);
        warning.setText(comment);

        return warning;
    }

    /** Creates a new ErrorInfoHeader based on the newly
     * supplied errorInfo value.
     *
     * @param errorInfo - the new URI value of the errorInfo.
     * @return the newly created ErrorInfoHeader object.
     */
    public ErrorInfoHeader createErrorInfoHeader(URI errorInfo) {
        if (errorInfo == null)
            throw new NullPointerException("null arg");
        return new ErrorInfo((GenericURI) errorInfo);
    }
    
    /**
     * Create a header from the given header text.
     * Header should not have the trailng crlf.
     * @throws ParseException 
     */
    public javax.sip.header.Header createHeader(String headerText) throws ParseException {
        SIPHeader sipHeader = StringMsgParser.parseSIPHeader(headerText.trim());
        if (sipHeader instanceof SIPHeaderList) {
            if (((SIPHeaderList<?>) sipHeader).size() > 1) {
                throw new ParseException(
                    "Only singleton allowed " + headerText,
                    0);
            } else if (((SIPHeaderList<?>) sipHeader).size() == 0) {
                try {
                    return (Header) ((SIPHeaderList<?>) sipHeader)
                        .getMyClass()
                        .newInstance();
                } catch (InstantiationException ex) {
                    ex.printStackTrace();
                    return null;
                } catch (IllegalAccessException ex) {
                    ex.printStackTrace();
                    return null;
                }
            } else {
                return (Header) ((SIPHeaderList<?>) sipHeader).getFirst();
            }
        } else {
            return (Header) sipHeader;
        }
        
    }

    /** Create and parse a header.
     *
     * @param headerName -- header name for the header to parse.
     * @param headerValue -- header value for the header to parse.
     * @throws ParseException
     * @return  the parsed sip header
     */
    public javax.sip.header.Header createHeader(
        String headerName,
        String headerValue)
        throws java.text.ParseException {
        if (headerName == null)
            throw new NullPointerException("header name is null");
        String hdrText =
            new StringBuilder()
                .append(headerName)
                .append(":")
                .append(headerValue)
                .toString();
        return createHeader(hdrText);
        
    }

    /** Create and return a list of headers.
     *@param headers -- list of headers.
     *@throws ParseException -- if a parse exception occurs or a List
     * of that type of header is not alowed.
     *@return a List containing the headers.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public List<SIPHeader> createHeaders(String headers)
        throws java.text.ParseException {
        if (headers == null)
            throw new NullPointerException("null arg!");
        SIPHeader shdr = StringMsgParser.parseSIPHeader(headers);
        if (shdr instanceof SIPHeaderList)
            return (SIPHeaderList) shdr;
        else
            throw new ParseException(
                "List of headers of this type is not allowed in a message",
                0);
    }

    /** Create a ReferTo Header.
     *@param address -- address for the header.
     */
    public ReferToHeader createReferToHeader(Address address) {
        if (address == null)
            throw new NullPointerException("null address!");
        ReferTo referTo = new ReferTo();
        referTo.setAddress(address);
        return referTo;
    }

    /** Create a ReferredBy Header.
     *
     *  pmusgrave
     *
     *@param address -- address for the header.
     *
     * TODO: Once interfaces are in javax, change the type to MinSEHeader
     * and add to HeaderFactory. - pmusgrave

     */
    public ReferredByHeader createReferredByHeader(Address address) {
        if (address == null)
            throw new NullPointerException("null address!");
        ReferredBy referredBy = new ReferredBy();
        referredBy.setAddress(address);
        return referredBy;
    }

    /**
     * Create a Replaces header with a call Id, to and from tag.
     *
     * TODO: Once interfaces are in javax, change the type to MinSEHeader
     * and add to HeaderFactory. - pmusgrave
     * pmusgrave
     */
    public ReplacesHeader createReplacesHeader(String callId, String toTag,
                String fromTag) throws ParseException
    {
        Replaces replaces = new Replaces();
        replaces.setCallId(callId);
        replaces.setFromTag(fromTag);
        replaces.setToTag(toTag);

        return replaces;
    }

    /**
     * Create a Join header with a call Id, to and from tag.
     *
     */
    public JoinHeader createJoinHeader(String callId, String toTag,
                String fromTag) throws ParseException
    {
        Join join = new Join();
        join.setCallId(callId);
        join.setFromTag(fromTag);
        join.setToTag(toTag);

        return join;
    }


    /*
     * (non-Javadoc)
     * @see javax.sip.header.HeaderFactory#createSIPETagHeader(java.lang.String)
     */
    public SIPETagHeader createSIPETagHeader(String etag) throws ParseException {
        return new SIPETag(etag);
    }

    /*
     * (non-Javadoc)
     * @see javax.sip.header.HeaderFactory#createSIPIfMatchHeader(java.lang.String)
     */
    public SIPIfMatchHeader createSIPIfMatchHeader(String etag) throws ParseException {
        return new SIPIfMatch(etag);
    }

    //////////////////////////////////////////////////////////////////////////
    // The following headers are not part of the JSIP spec.
    // They are IMS headers
    // (contributed by Miguel Freitas - PT Inovacao and Telecommunications Institute)
    ///////////////////////////////////////////////////////////////////////////

    /**
     * creates a P-Access-Network-Info header
     * @return newly created P-Access-Network-Info header
     */
    public PAccessNetworkInfoHeader createPAccessNetworkInfoHeader()
    {
        PAccessNetworkInfo accessNetworkInfo = new PAccessNetworkInfo();

        return accessNetworkInfo;
    }


    /**
     * P-Asserted-Identity header
     * @param address - Address
     * @return newly created P-Asserted-Identity header
     * @throws ParseException
     * @throws NullPointerException
     */
    public PAssertedIdentityHeader createPAssertedIdentityHeader(Address address)
        throws NullPointerException, ParseException
    {
        if (address == null)
            throw new NullPointerException("null address!");

        PAssertedIdentity assertedIdentity = new PAssertedIdentity();
        assertedIdentity.setAddress(address);

        return assertedIdentity;


    }


    /**
     * Creates a new P-Associated-URI header based on the supplied address
     * @param assocURI - Address
     * @return newly created P-Associated-URI header
     * @throws NullPointerException if the supplied address is null
     * @throws ParseException
     */
    public PAssociatedURIHeader createPAssociatedURIHeader(Address assocURI)
    {
        if (assocURI == null)
        throw new NullPointerException("null associatedURI!");

        PAssociatedURI associatedURI = new PAssociatedURI();
        associatedURI.setAddress(assocURI);

        return associatedURI;
    }




    /**
     * P-Called-Party-ID header
     * @param address - Address
     * @return newly created P-Called-Party-ID header
     * @throws NullPointerException
     * @throws ParseException
     */
    public PCalledPartyIDHeader createPCalledPartyIDHeader(Address address)
    {
        if (address == null)
            throw new NullPointerException("null address!");

        PCalledPartyID calledPartyID = new PCalledPartyID();
        calledPartyID.setAddress(address);

        return calledPartyID;
    }



    /**
     * P-Charging-Function-Addresses header
     * @return newly created P-Charging-Function-Addresses header
     */
    public PChargingFunctionAddressesHeader createPChargingFunctionAddressesHeader()
    {
        PChargingFunctionAddresses cfa = new PChargingFunctionAddresses();

        return cfa;
    }


    /**
     * P-Charging-Vector header
     * @param icid - icid string
     * @return newly created P-Charging-Vector header
     * @throws NullPointerException
     * @throws ParseException
     */
    public PChargingVectorHeader createChargingVectorHeader(String icid)
        throws ParseException
    {
        if (icid == null)
        throw new NullPointerException("null icid arg!");

        PChargingVector chargingVector = new PChargingVector();
        chargingVector.setICID(icid);

        return chargingVector;

    }


    /**
     * P-Media-Authorization header
     * @param token - token string
     * @return newly created P-Media-Authorizarion header
     * @throws InvalidArgumentException
     * @throws ParseException
     */
    public PMediaAuthorizationHeader createPMediaAuthorizationHeader(String token)
        throws InvalidArgumentException, ParseException
    {
        if (token == null || token == "")
            throw new InvalidArgumentException("The Media-Authorization-Token parameter is null or empty");


        PMediaAuthorization mediaAuthorization = new PMediaAuthorization();
        mediaAuthorization.setMediaAuthorizationToken(token);

        return mediaAuthorization;
    }


    /**
     * P-Preferred-Identity header
     * @param address - Address
     * @return newly created P-Preferred-Identity header
     * @throws NullPointerException
     */
    public PPreferredIdentityHeader createPPreferredIdentityHeader(Address address)
    {
        if (address == null)
            throw new NullPointerException("null address!");

        PPreferredIdentity preferredIdentity = new PPreferredIdentity();
        preferredIdentity.setAddress(address);

        return preferredIdentity;

    }

    /**
     * P-Visited-Network-ID header
     * @return newly created P-Visited-Network-ID header
     */
    public PVisitedNetworkIDHeader createPVisitedNetworkIDHeader()
    {
        PVisitedNetworkID visitedNetworkID = new PVisitedNetworkID();

        return visitedNetworkID;
    }



    /**
     * PATH header
     * @param address - Address
     * @return newly created Path header
     * @throws NullPointerException
     * @throws ParseException
     */
    public PathHeader createPathHeader(Address address)
    {
        if (address == null)
            throw new NullPointerException("null address!");


        Path path = new Path();
        path.setAddress(address);

        return path;
    }


    /**
     * Privacy header
     * @param privacyType - privacy type string
     * @return newly created Privacy header
     * @throws NullPointerException
     */
    public PrivacyHeader createPrivacyHeader(String privacyType)
    {
        if (privacyType == null)
            throw new NullPointerException("null privacyType arg");

        Privacy privacy = new Privacy(privacyType);

        return privacy;

    }


    /**
     * Service-Route header
     * @param address - Address
     * @return newly created Service-Route header
     * @throws NullPointerException
     */
    public ServiceRouteHeader createServiceRouteHeader(Address address)
    {
        if (address == null)
            throw new NullPointerException("null address!");

        ServiceRoute serviceRoute = new ServiceRoute();
        serviceRoute.setAddress(address);

        return serviceRoute;

    }

    /**
     * Security-Server header
     * @return newly created Security-Server header
     */
    public SecurityServerHeader createSecurityServerHeader()
    {
        SecurityServer secServer = new SecurityServer();
        return secServer;
    }

    /**
     * Security-Client header
     * @return newly created Security-Client header
     */
    public SecurityClientHeader createSecurityClientHeader()
    {
        SecurityClient secClient = new SecurityClient();
        return secClient;
    }

    /**
     * Security-Verify header
     * @return newly created Security-Verify header
     */
    public SecurityVerifyHeader createSecurityVerifyHeader()
    {
        SecurityVerify secVerify = new SecurityVerify();
        return secVerify;
    }

    /**
     * @return the newly create P-User-Database header.
     * Please note that this is not a SIP/TEL uri. It is a
     * DIAMETER AAA URI.
     */
    public PUserDatabaseHeader createPUserDatabaseHeader(String databaseName)
    {
        if((databaseName ==null)||(databaseName.equals(" ")))
            throw new NullPointerException("Database name is null");

        PUserDatabase pUserDatabase = new PUserDatabase();
        pUserDatabase.setDatabaseName(databaseName);

        return pUserDatabase;
    }


    /**
     * 
     * @return The newly created P-Profile-Key header.
     *
     */
    public PProfileKeyHeader createPProfileKeyHeader(Address address)
    {
        if (address ==null)
            throw new NullPointerException("Address is null");
        PProfileKey pProfileKey = new PProfileKey();
        pProfileKey.setAddress(address);

        return pProfileKey;
    }

    /**
     * 
     * @return The newly created P-Served-User header.
     */
    public PServedUserHeader createPServedUserHeader(Address address)
    {
        if(address==null)
            throw new NullPointerException("Address is null");
        PServedUser psu = new PServedUser();
        psu.setAddress(address);

        return psu;
    }
    /**
     * @return The newly created P-Preferred-Service header.
     */
    public PPreferredServiceHeader createPPreferredServiceHeader()
    {
        PPreferredService pps = new PPreferredService();
        return pps;
    }

    /**
     *
     * @return The newly created P-Asserted-Service header.
     */
    public PAssertedServiceHeader createPAssertedServiceHeader()
    {
        PAssertedService pas = new PAssertedService();
        return pas;
    }

    /**
     * Creates a new SessionExpiresHeader based on the newly supplied expires value.
     *
     * @param expires - the new integer value of the expires.
     * @throws InvalidArgumentException if supplied expires is less
     * than zero.
     * @return the newly created SessionExpiresHeader object.
     *
     */
    public SessionExpiresHeader createSessionExpiresHeader(int expires)
        throws InvalidArgumentException {
        if (expires < 0)
            throw new InvalidArgumentException("bad value " + expires);
        SessionExpires s = new SessionExpires();
        s.setExpires(expires);

        return s;
    }
    
    
    /**
     * Create a new Request Line from a String.
     * 
     */
    public SipRequestLine createRequestLine(String requestLine)  throws ParseException {
        
        RequestLineParser requestLineParser = new RequestLineParser(requestLine);
        return (SipRequestLine) requestLineParser.parse();
    }
    
    /**
     * Create a new StatusLine from a String.
     */
    public SipStatusLine createStatusLine(String statusLine) throws ParseException {
        StatusLineParser statusLineParser = new StatusLineParser(statusLine);
        return (SipStatusLine) statusLineParser.parse();
    }


    
    /**
     * Create and return a references header.
     * 
     * @param callId
     * @param rel
     * @return
     * @throws ParseException
     */

    public ReferencesHeader createReferencesHeader(String callId, String rel) throws ParseException {
        ReferencesHeader retval = new References();
        retval.setCallId(callId);
        retval.setRel(rel);
        return retval;
    }
    

    /**
     * Create and return a Target Dialog header.
     * 
     * @param callId
     * @return
     * @throws ParseException
     */

    
    public TargetDialogHeader createTargetDialogHeader(String callId) throws ParseException {
    	        if (callId == null)
    	            throw new NullPointerException("callId shouldn't be null or empty");
    	       TargetDialog t = new TargetDialog();
    	        t.setCallId(callId);
    	        return t;
    }
    
    /**
     * Create and return a Diversion header.
     * 
     * @param address
     * @return
     * @throws ParseException
     */
    public DiversionHeader createDiversionHeader(Address address) throws ParseException {
		 if (address == null)
	            throw new NullPointerException("Address shouldn't be null or empty");
	       Diversion d = new Diversion();
	        d.setAddress(address);
	        return d;
	}
    
    @Override
    public ResourcePriorityHeader createResourcePriorityHeader(String namespace, String priority) throws ParseException {
        if (namespace == null || priority == null) {
            throw new ParseException("Namespace and Priority cannot be null", 0);
        }
        ResourcePriority resourcePriority = new ResourcePriority(namespace, priority);
        return resourcePriority;
    }

    @Override
    public AcceptResourcePriorityHeader createAcceptResourcePriorityHeader(String namespace, String priority) throws ParseException {
        if (namespace == null || priority == null) {
            throw new ParseException("Namespace and Priority cannot be null", 0);
        }
        AcceptResourcePriority acceptResourcePriority = new AcceptResourcePriority(namespace, priority);
        return acceptResourcePriority;
    }
    
   
    //////////////////////////////////////////////////////////
    // Constructor
    //////////////////////////////////////////////////////////
    /**
     * Default constructor.
     */
    public HeaderFactoryImpl() {
        stripAddressScopeZones
            = Boolean.getBoolean("gov.nist.core.STRIP_ADDR_SCOPES");
    }
}
