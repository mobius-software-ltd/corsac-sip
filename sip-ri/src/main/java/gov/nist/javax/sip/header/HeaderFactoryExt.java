package gov.nist.javax.sip.header;

import java.text.ParseException;
import java.util.List;

import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;

import gov.nist.javax.sip.header.extensions.AcceptResourcePriorityHeader;
import gov.nist.javax.sip.header.extensions.DiversionHeader;
import gov.nist.javax.sip.header.extensions.JoinHeader;
import gov.nist.javax.sip.header.extensions.ReferencesHeader;
import gov.nist.javax.sip.header.extensions.ReferredByHeader;
import gov.nist.javax.sip.header.extensions.ReplacesHeader;
import gov.nist.javax.sip.header.extensions.Resource;
import gov.nist.javax.sip.header.extensions.ResourcePriorityHeader;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import gov.nist.javax.sip.header.extensions.TargetDialogHeader;
import gov.nist.javax.sip.header.ims.PAccessNetworkInfoHeader;
import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import gov.nist.javax.sip.header.ims.PAssertedServiceHeader;
import gov.nist.javax.sip.header.ims.PAssociatedURIHeader;
import gov.nist.javax.sip.header.ims.PCalledPartyIDHeader;
import gov.nist.javax.sip.header.ims.PChargingFunctionAddressesHeader;
import gov.nist.javax.sip.header.ims.PChargingVectorHeader;
import gov.nist.javax.sip.header.ims.PMediaAuthorizationHeader;
import gov.nist.javax.sip.header.ims.PPreferredIdentityHeader;
import gov.nist.javax.sip.header.ims.PPreferredServiceHeader;
import gov.nist.javax.sip.header.ims.PProfileKeyHeader;
import gov.nist.javax.sip.header.ims.PServedUserHeader;
import gov.nist.javax.sip.header.ims.PUserDatabaseHeader;
import gov.nist.javax.sip.header.ims.PVisitedNetworkIDHeader;
import gov.nist.javax.sip.header.ims.PathHeader;
import gov.nist.javax.sip.header.ims.PrivacyHeader;
import gov.nist.javax.sip.header.ims.SecurityClientHeader;
import gov.nist.javax.sip.header.ims.SecurityServerHeader;
import gov.nist.javax.sip.header.ims.SecurityVerifyHeader;
import gov.nist.javax.sip.header.ims.ServiceRouteHeader;

/**
 * Header factory extensions. These will be included in the next release of
 * JAIN-SIP.
 * 
 * @since 2.0
 *
 */
public interface HeaderFactoryExt extends HeaderFactory {
    
    /**
     * Create a RequestLine from a String
     * @throws ParseException 
     */
    public SipRequestLine createRequestLine(String requestLine) throws ParseException;
    
    
    /**
     * Create a StatusLine from a String.
     */
    public SipStatusLine createStatusLine(String statusLine) throws ParseException;
    
    
    /**
     * Create a ReferredBy Header.
     *
     * @param address --
     *            address for the header.
     *
     */
    public ReferredByHeader createReferredByHeader(Address address);

    /**
     *
     * Create a Replaces header with a call Id, to and from tag.
     *
     * @param callId -
     *            the call id to use.
     * @param toTag -
     *            the to tag to use.
     * @param fromTag -
     *            the fromTag to use.
     *
     */
    public ReplacesHeader createReplacesHeader(String callId, String toTag,
            String fromTag) throws ParseException;

    /**
     * creates a P-Access-Network-Info header.
     *
     * @return newly created P-Access-Network-Info header
     */
    public PAccessNetworkInfoHeader createPAccessNetworkInfoHeader();

    /**
     * P-Asserted-Identity header
     *
     * @param address -
     *            Address
     * @return newly created P-Asserted-Identity header
     * @throws ParseException
     * @throws NullPointerException
     */
    public PAssertedIdentityHeader createPAssertedIdentityHeader(Address address)
            throws NullPointerException, ParseException;

    /**
     * Creates a new P-Associated-URI header based on the supplied address
     *
     * @param assocURI -
     *            Address
     * @return newly created P-Associated-URI header
     * @throws NullPointerException
     *             if the supplied address is null
     */
    public PAssociatedURIHeader createPAssociatedURIHeader(Address assocURI);

    /**
     * P-Called-Party-ID header
     *
     * @param address -
     *            Address
     * @return newly created P-Called-Party-ID header
    */
    public PCalledPartyIDHeader createPCalledPartyIDHeader(Address address);

    /**
     * P-Charging-Function-Addresses header
     *
     * @return newly created P-Charging-Function-Addresses header
     */
    public PChargingFunctionAddressesHeader createPChargingFunctionAddressesHeader();

    /**
     * P-Charging-Vector header
     *
     * @param icid -
     *            icid string
     * @return newly created P-Charging-Vector header
     * @throws NullPointerException
     * @throws ParseException
     */
    public PChargingVectorHeader createChargingVectorHeader(String icid) throws ParseException;

     /**
     * P-Media-Authorization header
     * @param token - token string
     * @return newly created P-Media-Authorizarion header
     * @throws InvalidArgumentException
     * @throws ParseException
     */
    public PMediaAuthorizationHeader createPMediaAuthorizationHeader(String token)
        throws InvalidArgumentException, ParseException;

    /**
     * P-Preferred-Identity header
     * @param address - Address
     * @return newly created P-Preferred-Identity header
     * @throws NullPointerException
     */
    public PPreferredIdentityHeader createPPreferredIdentityHeader(Address address);

    /**
     * P-Visited-Network-ID header
     * @return newly created P-Visited-Network-ID header
     */
    public PVisitedNetworkIDHeader createPVisitedNetworkIDHeader();

    /**
     * PATH header
     * @param address - Address
     * @return newly created Path header
     */
    public PathHeader createPathHeader(Address address);

    /**
     * Privacy header
     * @param privacyType - privacy type string
     * @return newly created Privacy header
     * @throws NullPointerException
     */
    public PrivacyHeader createPrivacyHeader(String privacyType);


    /**
     * Service-Route header
     * @param address - Address
     * @return newly created Service-Route header
     * @throws NullPointerException
     */
    public ServiceRouteHeader createServiceRouteHeader(Address address);

    /**
     * Security-Server header
     * @return newly created Security-Server header
     */
    public SecurityServerHeader createSecurityServerHeader();

    /**
     * Security-Client header
     * @return newly created Security-Client header
     */
    public SecurityClientHeader createSecurityClientHeader();


    /**
     * Security-Verify header
     * @return newly created Security-Verify header
     */
    public SecurityVerifyHeader createSecurityVerifyHeader();


    /**
     * Creates a new SessionExpiresHeader based on the newly supplied expires value.
     *
     * @param expires - the new integer value of the expires.
     * @throws InvalidArgumentException if supplied expires is less
     * than zero.
     * @return the newly created SessionExpiresHeader object.
     *
     */
    public SessionExpiresHeader createSessionExpiresHeader(int expires) throws InvalidArgumentException ;

    /**
     *
     * Create a Join header with a call Id, to and from tag.
     *
     * @param callId -
     *            the call id to use.
     * @param toTag -
     *            the to tag to use.
     * @param fromTag -
     *            the fromTag to use.
     *
     */
    public JoinHeader createJoinHeader(String callId, String toTag,
            String fromTag) throws ParseException;

    /**
     * Create a P-User-Database header.
     * 
     * @return the newly created P-User-Database header
     * @param databaseName the database name, that may be an IP:port or a domain name.
     */
    public PUserDatabaseHeader createPUserDatabaseHeader(String databaseName);


    /**
     * Create a P-Profile-Key header.
     * 
     * @param address
     * @return The newly created P-Profile-Key header
     */
    public PProfileKeyHeader createPProfileKeyHeader(Address address);

    /**
     * Create a P-Served-User header.
     * 
     * @param address of the served user.
     * @return The newly created P-Served-User Header.
     */
    public PServedUserHeader createPServedUserHeader(Address address);

    /**
     * Create a P-Preferred-Service header.
     * 
     * @return The newly created P-Preferred-Service Header.
     */
    public PPreferredServiceHeader createPPreferredServiceHeader();

    /**
     * Create an AssertedService Header
     *
     * @return The newly created P-Asserted-Service Header.
     */
    public PAssertedServiceHeader createPAssertedServiceHeader();
    
    
    /**
     * Create a References header.
     * 
     * @param callId -- the referenced call Id.
     * 
     * @return the newly created References header.
     * @throws ParseException 
     */
    public ReferencesHeader createReferencesHeader(String callId, String rel) throws ParseException;
    
    
    /**
     * Create a Target Dialog header.
     * 
     * @param callId -- the target call Id.
     * 
     * @return the newly created Target Dialog header.
     */
    public TargetDialogHeader createTargetDialogHeader(String callId) throws ParseException;
    
    
    /**
     * Create a Diversion header.
     * 
     * @param address -- the diverted URI.
     * 
     * @return the newly created Diversion header.
     */
    public DiversionHeader createDiversionHeader(Address address) throws ParseException;
    
    /**
     * Create a ResourcePriority header.
     * 
     * @param resource -- the list of resources.
     * 
     * @return the newly created ResourcePriority header.
     * @throws ParseException if there is an error parsing the header
     */
    public ResourcePriorityHeader createResourcePriorityHeader(List<Resource> resource) throws ParseException;

    /**
     * Create an AcceptResourcePriority header.
     * 
     * @param resource -- the list of resources.
     * 
     * @return the newly created AcceptResourcePriority header.
     * @throws ParseException if there is an error parsing the header
     */
    public AcceptResourcePriorityHeader createAcceptResourcePriorityHeader(List<Resource> resource) throws ParseException;

    /**
     * Create a header from a string. The string is assumed to be in the 
     * name:value format. The trailing CRLF (if any ) will be stripped
     * before parsing this. The header should be a singleton.
     */
    public Header createHeader(String header) throws ParseException;

}
