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

import static gov.nist.javax.sip.ListeningPointExt.WS;
import static gov.nist.javax.sip.ListeningPointExt.WSS;
import static javax.sip.ListeningPoint.TCP;
import static javax.sip.ListeningPoint.TLS;
import static javax.sip.ListeningPoint.UDP;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;

import gov.nist.javax.sip.util.UtilsExtension;

/**
 * @author hrosa (henrique.rosa@telestax.com) on 2019-06-06
 */
public class Rfc3263HopperFactory implements HopperFactory {

    public static final int TCP_PORT = 5060;
    public static final int UDP_PORT = 5060;
    public static final int TLS_PORT = 5061;
    public static final int WS_PORT = 5062;
    public static final int WSS_PORT = 5063;

    private DNSLookupPerformer dnsLookupPerformer;
    
    public Rfc3263HopperFactory() {        
    }

    public void setLookupPerformer(DNSLookupPerformer perfomer) {
    	this.dnsLookupPerformer = perfomer;
    }
    
    @Override
    public Hopper build(SipURI requestURI) {
        // Find transport
        final String transport = findTransport(requestURI);

        if (transport!=null) {
            // Find port
            final Integer port = findPort(requestURI, transport);

            if (port!=null) {
                if (UtilsExtension.isIpAddress(requestURI.getHost())) {
                    // Single hop for a concrete IP address and port over a specific transport
                    return new IpAddressHopper(port, requestURI.getHost(), transport);
                } else {
                    // Hop list of A records top-down
                    return new DnsAHopper(requestURI.getHost(), port, transport, this.dnsLookupPerformer);
                }
            } else {
                // Perform NAPTR hopping
            	return new DnsNaptrHopper(requestURI, transport, this.dnsLookupPerformer, this);
            }
        } else {
            // Hop list of NAPTR records ordered by priority
            return new DnsNaptrHopper(requestURI, ListeningPoint.UDP, this.dnsLookupPerformer, this);
        }
    }

    private String findTransport(SipURI requestURI) {
        final String transport;

        final String transportParam = requestURI.getTransportParam();
        if (transportParam!=null) {
            // If transport is specified in request URI then use it
            transport = transportParam;
        } else if (UtilsExtension.isIpAddress(requestURI.getHost())) {
            // If target is numeric IP then use UDP
            transport = (requestURI.isSecure() ? TCP : UDP).toLowerCase();
        } else if (requestURI.getPort() != -1) {
            // TODO We may another transport besides UDP if msg > MTU for example!!
            // If no transport and target is not numeric BUT port is specified, then use UDP
            transport = (requestURI.isSecure() ? TCP : UDP).toLowerCase();
        } else {
            // If none of the above, a DNS NAPTR lookup must be done to discover available transports for target address
            transport = null;
        }

        return transport;
    }


    private Integer findPort(SipURI requestURI, String transport) {
        final Integer port;
        if (requestURI.getPort() != -1) {
            // If port is specified then use it
            port = requestURI.getPort();
        } else if (UtilsExtension.isIpAddress(requestURI.getHost())) {
            // If target is numeric IP address and no port is specified, used default port for the selected transport
            port = getDefaultPort(transport);
        } else {
            // Otherwise, perform a DNS SRV query
            port = null;
        }
        return port;
    }

    public static int getDefaultPort(String transport) {
        switch (transport.toUpperCase()) {
            case TCP:
                return TCP_PORT;

            case UDP:
                return UDP_PORT;

            case TLS:
                return TLS_PORT;

            case WS:
                return WS_PORT;

            case WSS:
                return WSS_PORT;

            default:
                // TODO log warning here!
                return UDP_PORT;
        }
    }

}
