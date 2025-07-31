/*
 * Mobius Software LTD
 * Copyright 2019 - 2023, Mobius Software LTD and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package gov.nist.javax.sip.dns;

import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.address.Hop;
import javax.sip.address.SipURI;
import javax.sip.address.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author hrosa (henrique.rosa@telestax.com) on 2019-06-06
 */
public class Rfc3263Hopper extends CompositeHopper {
    private static final Logger log = LogManager.getLogger(Rfc3263Hopper.class);
    public static final String DNS_RESOLVE_PARAM = "dns_resolve";

    private final Hopper hopper;

    // Find target URI that needs to be resolved
    // -1 means request URI
    // x >= 0 means index of Route Header
    private int dnsUriIndex = -1;
    private boolean dnsResolveFlag = false;
    private int hopCount = 0;
    private Hop currentHop = null;
    private SIPRequest request;

    public Rfc3263Hopper(SIPRequest request, Hopper hopper) {
        super(null);
        this.hopper = hopper;
        this.request = request;
        dnsUriIndex = findRequestUriIndex(request);
    }

    public Rfc3263Hopper(SIPRequest request, HopperFactory hopperFactory) {
        super(hopperFactory);
        this.request = request;
        this.hopper = hopperFactory.build(findRequestURI(request));
        dnsUriIndex = findRequestUriIndex(request);
    }

    public int getHopCount() {
        return hopCount;
    }

    public Hop getCurrentHop() {
        return currentHop;
    }

    public void setRequest(SIPRequest request) {
        this.request = request;
    }

    public Hopper getHopper() {
        return hopper;
    }

    public int getDnsUriIndex() {
        return dnsUriIndex;
    }

    public boolean isDnsResolveFlag() {
        return dnsResolveFlag;
    }

    @Override
    public Hop hop() {
        hopCount = hopCount + 1;
        currentHop = this.hopper.hop();
        return currentHop;
    }

    public SIPRequest getRequest() {
        return request;
    }

    private SipURI findRequestURI(SIPRequest request) {
        final int index = findRequestUriIndex(request);
        if (index > -1) {
            return (SipURI) request.getRouteHeaders().get(index).getAddress().getURI();
        }
        return (SipURI) request.getRequestURI();
    }

    private boolean containsURIResolve(URI uri) {
        boolean flagDetected = false;
        if (uri.isSipURI()) {
            final SipURI sipUri = (SipURI) uri;
            if (sipUri.toString().contains(Rfc3263Hopper.DNS_RESOLVE_PARAM)) {
                flagDetected = true;
            }
        }
        return flagDetected;
    }

    
    private static final int FLAG_NOT_FOUND = -2;
    /**
     * returns the index to route headers where dns_resolve flag was found
     * returns -1 if requestURI contains flag
     * returns -2 otherwise
     * @param message
     * @return 
     */
    private int findDnsResolveFlag(SIPRequest message) {
        int dnsFlagIndex = FLAG_NOT_FOUND;

        final RouteList routeHeaders = message.getRouteHeaders();
        if (routeHeaders != null && !routeHeaders.isEmpty()) {
            // !!! PROPRIETARY BEHAVIOUR !!!
            // CCN-3662: Find Route Header with dns_resolve param from top
            for (int i = 0; i < routeHeaders.size(); i++) {
                final Route route = routeHeaders.get(i);
                final SipURI routeUri = (SipURI) route.getAddress().getURI();
                dnsResolveFlag = containsURIResolve(routeUri);
                if (dnsResolveFlag) {
                    dnsFlagIndex = i;
                    log.debug("flag found in route header. Stop on first match");
                    break;
                }
            }
        }
        if (!dnsResolveFlag) {
            //if not route found, try with requestURI
            dnsResolveFlag = containsURIResolve(message.getRequestURI());
            if (dnsResolveFlag) {
                log.debug("found flag in requestURI");
                dnsFlagIndex = -1;
            }
        }

        return dnsFlagIndex;
    }

    private int findRequestUriIndex(SIPRequest message) {
        int dnsFlag = findDnsResolveFlag(message);
        if (dnsFlag > FLAG_NOT_FOUND) {
            return dnsFlag;
        }
        // Use top most Route Header preferably
        final RouteList routeHeaders = message.getRouteHeaders();
        if (routeHeaders != null && !routeHeaders.isEmpty()) {
            dnsResolveFlag = containsURIResolve(routeHeaders.get(0).getAddress().getURI());
            return 0;
        }

        dnsResolveFlag = containsURIResolve(message.getRequestURI());
        return -1;
    }

    @Override
    public String toString() {
        return "Rfc3263Hopper{" + "dnsUriIndex=" + dnsUriIndex + ", dnsResolveFlag=" + dnsResolveFlag + ", hopCount=" + hopCount + ", currentHop=" + currentHop + ", request=" + request + '}';
    }

}
