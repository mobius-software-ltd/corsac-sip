package gov.nist.javax.sip.clientauthutils;

import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Timer;

import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogWriter;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;

/**
 * The class handles authentication challenges, caches user credentials and takes care (through
 * the SecurityAuthority interface) about retrieving passwords.
 *
 * @author Emil Ivov
 * @author Jeroen van Bemmel
 * @author M. Ranganathan
 *
 * @since 2.0
 */
public class AuthenticationHelperImpl implements AuthenticationHelper {
    private static StackLogger logger = CommonLogger.getLogger(AuthenticationHelperImpl.class);

    /**
     * Credentials cached so far.
     */
    private CredentialsCache cachedCredentials;

    /**
     * The account manager for the system. Stores user credentials.
     */
    private Object accountManager = null;

    /*
     * Header factory for this security manager.
     */
    private HeaderFactory headerFactory;

    Timer timer;

    /**
     * Default constructor for the security manager. There is one Account manager. There is one
     * SipSecurity manager for every user name,
     *
     * @param sipStack -- our stack.
     * @param accountManager -- an implementation of the AccountManager interface.
     * @param headerFactory -- header factory.
     */
    public AuthenticationHelperImpl(SIPTransactionStack sipStack, AccountManager accountManager,
            HeaderFactory headerFactory) {
        this.accountManager = accountManager;
        this.headerFactory = headerFactory;
        this.cachedCredentials = new CredentialsCache(((SIPTransactionStack) sipStack).getTimer());
    }

    /**
     * Default constructor for the security manager. There is one Account manager. There is one
     * SipSecurity manager for every user name,
     *
     * @param sipStack -- our stack.
     * @param accountManager -- an implementation of the AccountManager interface.
     * @param headerFactory -- header factory.
     */
    public AuthenticationHelperImpl(SIPTransactionStack sipStack, SecureAccountManager accountManager,
            HeaderFactory headerFactory) {
        this.accountManager = accountManager;
        this.headerFactory = headerFactory;
        this.cachedCredentials = new CredentialsCache(((SIPTransactionStack) sipStack).getTimer());
    }

    @Override
    public ClientTransaction handleChallenge(Response challenge,
            ClientTransaction challengedTransaction, SipProvider transactionCreator, int cacheTime)
            throws SipException, NullPointerException {
        return handleChallenge(challenge, challengedTransaction, transactionCreator, cacheTime, false);
    }

    @Override
    public ClientTransaction handleChallenge(Response challenge,
            ClientTransaction challengedTransaction, SipProvider transactionCreator, int cacheTime, boolean looseRouting)
            throws SipException, NullPointerException {
        try {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug("handleChallenge: " + challenge);

            SIPRequest challengedRequest = ((SIPRequest) challengedTransaction.getRequest());
            Request reoriginatedRequest = null;

            if (challengedRequest.getToTag() != null ||
                    challengedTransaction.getDialog() == null ||
                    challengedTransaction.getDialog().getState() != DialogState.CONFIRMED) {
                reoriginatedRequest = (Request) challengedRequest.clone();
            } else {
                reoriginatedRequest = challengedTransaction.getDialog().createRequest(challengedRequest.getMethod());
                Iterator<String> headerNames = challengedRequest.getHeaderNames();
                while (headerNames.hasNext()) {
                    String headerName = headerNames.next();
                    if (reoriginatedRequest.getHeader(headerName) == null) {
                        ListIterator<SIPHeader> iterator = challengedRequest.getHeaders(headerName);
                        while (iterator.hasNext()) {
                            reoriginatedRequest.addHeader(iterator.next());
                        }
                    }
                }
            }

            removeBranchID(reoriginatedRequest);

            if (challenge == null || reoriginatedRequest == null) {
                throw new NullPointerException("A null argument was passed to handle challenge.");
            }

            ListIterator<?> authHeaders = null;

            if (challenge.getStatusCode() == Response.UNAUTHORIZED) {
                authHeaders = challenge.getHeaders(WWWAuthenticateHeader.NAME);
            } else if (challenge.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED) {
                authHeaders = challenge.getHeaders(ProxyAuthenticateHeader.NAME);
            } else {
                throw new IllegalArgumentException("Unexpected status code ");
            }

            if (authHeaders == null) {
                throw new IllegalArgumentException(
                        "Could not find WWWAuthenticate or ProxyAuthenticate headers");
            }

            reoriginatedRequest.removeHeader(AuthorizationHeader.NAME);
            reoriginatedRequest.removeHeader(ProxyAuthorizationHeader.NAME);

            CSeqHeader cSeq = (CSeqHeader) reoriginatedRequest.getHeader(CSeqHeader.NAME);
            try {
                cSeq.setSeqNumber(cSeq.getSeqNumber() + 1l);
            } catch (InvalidArgumentException ex) {
                throw new SipException("Invalid CSeq -- could not increment : " + cSeq.getSeqNumber());
            }

            ClientTransaction retryTran = transactionCreator.getNewClientTransaction(reoriginatedRequest);

            WWWAuthenticateHeader authHeader = null;
            while (authHeaders.hasNext()) {
                authHeader = (WWWAuthenticateHeader) authHeaders.next();
                String realm = authHeader.getRealm();
                AuthorizationHeader authorization = null;
                if (this.accountManager instanceof SecureAccountManager) {
                    UserCredentialHash credHash = ((SecureAccountManager) this.accountManager).getCredentialHash(challengedTransaction, realm);
                    if (credHash == null) {
                        logger.logDebug("Could not find creds");
                        throw new SipException("Cannot find user creds for the given user name and realm");
                    }
                    authorization = this.getAuthorization(reoriginatedRequest.getMethod(), reoriginatedRequest.getRequestURI().toString(),
                            (reoriginatedRequest.getContent() == null) ? "" : new String(reoriginatedRequest.getRawContent()), authHeader, credHash);
                } else {
                    UserCredentials userCreds = ((AccountManager) this.accountManager).getCredentials(challengedTransaction, realm);

                    if (userCreds == null) {
                        throw new SipException("Cannot find user creds for the given user name and realm");
                    }

                    authorization = this.getAuthorization(reoriginatedRequest.getMethod(), reoriginatedRequest.getRequestURI().toString(),
                            (reoriginatedRequest.getContent() == null) ? "" : new String(reoriginatedRequest.getRawContent()), authHeader, userCreds);
                }

                if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                    logger.logDebug("Created authorization header: " + authorization.toString());
                }

                if (cacheTime != 0) {
                    String callId = challengedRequest.getCallId().getCallId();
                    cachedCredentials.cacheAuthorizationHeader(callId, authorization, cacheTime);
                }
                reoriginatedRequest.addHeader(authorization);
            }

            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
                logger.logDebug("Returning authorization transaction." + retryTran);
            }

            return retryTran;
        } catch (SipException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.logError("Unexpected exception ", ex);
            throw new SipException("Unexpected exception ", ex);
        }
    }

    private AuthorizationHeader getAuthorization(String method, String uri, String requestBody,
            WWWAuthenticateHeader authHeader, UserCredentials userCredentials) {
        String response = null;

        String qopList = authHeader.getQop();
        String qop = (qopList != null) ? "auth" : null;
        String nc_value = "00000001";
        String cnonce = "xyz";

        response = MessageDigestAlgorithm.calculateResponse(authHeader.getAlgorithm(),
                userCredentials.getUserName(), authHeader.getRealm(), userCredentials.getPassword(),
                authHeader.getNonce(), nc_value, cnonce, method, uri, requestBody, qop, logger);

        AuthorizationHeader authorization = null;
        try {
            if (authHeader instanceof ProxyAuthenticateHeader) {
                authorization = headerFactory.createProxyAuthorizationHeader(authHeader.getScheme());
            } else {
                authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
            }

            authorization.setUsername(userCredentials.getUserName());
            authorization.setRealm(authHeader.getRealm());
            authorization.setNonce(authHeader.getNonce());
            authorization.setParameter("uri", uri);
            authorization.setResponse(response);
            if (authHeader.getAlgorithm() != null) {
                authorization.setAlgorithm(authHeader.getAlgorithm());
            }

            if (authHeader.getOpaque() != null) {
                authorization.setOpaque(authHeader.getOpaque());
            }

            if (qop != null) {
                authorization.setQop(qop);
                authorization.setCNonce(cnonce);
                authorization.setNonceCount(Integer.parseInt(nc_value));
            }

            authorization.setResponse(response);

        } catch (ParseException ex) {
            throw new RuntimeException("Failed to create an authorization header!");
        }

        return authorization;
    }

    private AuthorizationHeader getAuthorization(String method, String uri, String requestBody,
            WWWAuthenticateHeader authHeader, UserCredentialHash userCredentials) {
        String response = null;

        String qopList = authHeader.getQop();
        String qop = (qopList != null) ? "auth" : null;
        String nc_value = "00000001";
        String cnonce = "xyz";

        response = MessageDigestAlgorithm.calculateResponse(authHeader.getAlgorithm(),
                userCredentials.getHashUserDomainPassword(), authHeader.getNonce(), nc_value,
                cnonce, method, uri, requestBody, qop, logger);

        AuthorizationHeader authorization = null;
        try {
            if (authHeader instanceof ProxyAuthenticateHeader) {
                authorization = headerFactory.createProxyAuthorizationHeader(authHeader.getScheme());
            } else {
                authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
            }

            authorization.setUsername(userCredentials.getUserName());
            authorization.setRealm(authHeader.getRealm());
            authorization.setNonce(authHeader.getNonce());
            authorization.setParameter("uri", uri);
            authorization.setResponse(response);
            if (authHeader.getAlgorithm() != null) {
                authorization.setAlgorithm(authHeader.getAlgorithm());
            }

            if (authHeader.getOpaque() != null) {
                authorization.setOpaque(authHeader.getOpaque());
            }

            if (qop != null) {
                authorization.setQop(qop);
                authorization.setCNonce(cnonce);
                authorization.setNonceCount(Integer.parseInt(nc_value));
            }

            authorization.setResponse(response);

        } catch (ParseException ex) {
            throw new RuntimeException("Failed to create an authorization header!");
        }

        return authorization;
    }

    private void removeBranchID(Request request) {
        ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
        viaHeader.removeParameter("branch");
    }

    @Override
    public void setAuthenticationHeaders(Request request) {
        SIPRequest sipRequest = (SIPRequest) request;
        String callId = sipRequest.getCallId().getCallId();

        request.removeHeader(AuthorizationHeader.NAME);
        Collection<AuthorizationHeader> authHeaders = this.cachedCredentials.getCachedAuthorizationHeaders(callId);
        if (authHeaders == null) {
            if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG))
                logger.logDebug("Could not find authentication headers for " + callId);
            return;
        }

        for (AuthorizationHeader authHeader : authHeaders) {
            request.addHeader(authHeader);
        }
    }

    @Override
    public void removeCachedAuthenticationHeaders(String callId) {
        if (callId == null)
            throw new NullPointerException("Null callId argument ");
        this.cachedCredentials.removeAuthenticationHeader(callId);
    }
}

