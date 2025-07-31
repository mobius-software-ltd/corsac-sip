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

import javax.sip.address.Hop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author hrosa (henrique.rosa@telestax.com) on 2019-06-17
 */
public class DnsSrvBatchHopper implements Hopper {

    private static final Logger log = LogManager.getLogger(DnsSrvBatchHopper.class);

    private final DNSLookupPerformer dnsLookup;
    private final String transport;
    private final String host;

    private DnsSrvQuery query;
    private Hopper srvHopper;
    private Hopper aHopper;

    public DnsSrvBatchHopper(DNSLookupPerformer dnsLookup, String service, String host, String transport) {
        this.dnsLookup = dnsLookup;
        this.transport = transport;
        this.host = host;
        this.query = new DnsSrvQuery(service.toLowerCase(), transport.toLowerCase(), host);
        this.srvHopper = new DnsSrvHopper(query.toString(), this.dnsLookup);
    }

    private String getDefaultTransport() {
        return transport;        
    }

    @Override
    public Hop hop() {
    	if(aHopper!=null) {
    		Hop nextHop = this.aHopper.hop();
            if (nextHop!=null) {
                if (log.isDebugEnabled()) {
                    log.debug("Found default A record for host " + this.host + " on " + nextHop);
                }
                return nextHop;
            } else {
                // End of line. No more A records available
                if (log.isDebugEnabled()) {
                    log.debug("No more hops available for host " + this.host);
                }
                return null;
            }
    	}
    	else {
    		Hop nextHop = this.srvHopper.hop();

            if (nextHop != null) {
                // There are still hops available for current query
                if (log.isDebugEnabled()) {
                    log.debug("Query " + this.query + " hopped to " + nextHop);
                }
                return nextHop;
            } else {
            	final String defaultTransport = getDefaultTransport();
                final int defaultPort = Rfc3263HopperFactory.getDefaultPort(defaultTransport);

                if (log.isDebugEnabled()) {
                    log.debug("Exhausted all SRV queries for host " + this.host + ". Will attempt to fetch A record using default port " + defaultPort + " and transport " + defaultTransport);
                }

                // Build hopper to go through A records
                this.aHopper = new DnsAHopper(this.host, defaultPort, defaultTransport, this.dnsLookup);

                // Delegate hopping to the A hopper
                return hop();
            }
    	}
    }

    private final class DnsSrvQuery {

        private final String service;
        private final String host;
        private final String transport;

        public DnsSrvQuery(String service, String transport, String host) {
            this.service = service;
            this.transport = transport;
            this.host = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        }

        @Override
        public String toString() {
            return "_" + service + "._" + transport + "." + host + ".";
        }

    }

}
