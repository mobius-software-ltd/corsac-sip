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

import java.util.Arrays;
import java.util.List;

import javax.sip.address.Hop;
import javax.sip.address.SipURI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.NAPTRRecord;

/**
 * @author hrosa (henrique.rosa@telestax.com) on 2019-06-06
 */
public class DnsNaptrHopper implements Hopper {

    private static final Logger log = LogManager.getLogger(DnsNaptrHopper.class);

    private final SipURI requestURI;
    private final String transport;
    private final DNSLookupPerformer dnsLookupPerformer;
   
    private final List<NAPTRRecord> naptrRecords;
    private Hopper recordHopper;
    private boolean forceSrv;

    public DnsNaptrHopper(SipURI sipURI, String transport, DNSLookupPerformer dnsLookupPerformer, HopperFactory hopperFactory) {
        this.requestURI = sipURI;
        this.transport = transport;
        this.dnsLookupPerformer = dnsLookupPerformer;
        
        // Perform NAPTR query
        this.naptrRecords = dnsLookupPerformer.performNAPTRLookup(sipURI.getHost(), sipURI.isSecure(), transport);
        this.forceSrv = this.naptrRecords == null || this.naptrRecords.isEmpty();
        if (log.isDebugEnabled()) {
            log.debug("NAPTR query for " + sipURI + " returned following results: " + Arrays.toString(this.naptrRecords.toArray()));
        }
    }

    @Override
    public Hop hop() {
        if (this.recordHopper == null) {
            if (this.naptrRecords==null || this.naptrRecords.isEmpty()) {
                if (this.forceSrv) {
                    // NPTR query wielded no results!
                    // Must force SRV queries for all supported transports
                    this.recordHopper = new DnsSrvBatchHopper(this.dnsLookupPerformer, requestURI.isSecure() ? "SIPS" : "SIP", requestURI.getHost(), transport);

                    if (log.isDebugEnabled()) {
                        log.debug("NAPTR query for " + requestURI + " wielded no results! Will force SRV queries for following transport: " + transport);
                    }

                    // Mark forceSRV to false to avoid infinite looping in cases where
                    // newly created hopper returns no hops
                    this.forceSrv = false;

                    // If hopper was created successfully, then use it to find out next hop
                    // Otherwise, stop hopping process here
                    return hop();
                } else {
                    // Exhausted all natural SRV hops
                    log.debug("NAPTR hopper exhausted all options!");
                    return null;
                }
            } else {
                // Hop to next NAPTR record
                final NAPTRRecord record = this.naptrRecords.remove(0);

                if (record.getFlags().toLowerCase().contains("s") && record.getService().toLowerCase().startsWith("sip")) {
                    // Perform SRV lookup on target host of current NAPTR record
                    if (log.isDebugEnabled()) {
                        log.debug("NAPTR record [" + record + "] will trigger SRV hopping for " + record.getAdditionalName());
                    }

                    this.recordHopper = new DnsSrvHopper(record.getAdditionalName().toString(), this.dnsLookupPerformer);
                    return hop();
                } else {
                    log.warn("NAPTR record [" + record + "] will be skipped because flags or service are unsupported");
                    return hop();
                }
            }
        } else {
            // We're in the middle of hopping through the result of an NAPTR record
            final Hop nextHop = this.recordHopper.hop();

            if (nextHop == null) {
                // No more hops available for given NAPTR record
                // Hop to next NAPTR record
                this.recordHopper = null;
                return hop();
            } else {
                // Return next available hop for current NAPTR record
                return nextHop;
            }
        }
    }

}
