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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.sip.ListeningPoint;
import javax.sip.address.Hop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import gov.nist.javax.sip.stack.HopImpl;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class DefaultDNSLookupPerformer implements DNSLookupPerformer {
	private static final Logger logger = LogManager.getLogger(DefaultDNSLookupPerformer.class);
	private static int DEFAULT_DNS_TIMEOUT_SECONDS = 1;
	private int dnsTimeout;
	
	public DefaultDNSLookupPerformer() {
		// https://code.google.com/p/jain-sip/issues/detail?id=162
		dnsTimeout = DEFAULT_DNS_TIMEOUT_SECONDS;
		Lookup.getDefaultResolver().setTimeout(Duration.ofSeconds(dnsTimeout));
	}
	
	/* (non-Javadoc)
	 * @see org.restcomm.ext.javax.sip.dns.DNSLookupPerformer#performSRVLookup(org.xbill.DNS.Name)
	 */
	public List<SRVRecord> performSRVLookup(String replacement) {
		if(logger.isDebugEnabled()) {
			logger.debug("doing SRV lookup for replacement " + replacement);
		}
		SRVRecord[] srvRecords = null;
		try {
			Lookup lookup = new Lookup(replacement, Type.SRV);
			Lookup.getDefaultResolver().setTimeout(Duration.ofSeconds(dnsTimeout));
			srvRecords = (SRVRecord[])lookup.run();
		} catch (TextParseException e) {
			logger.error("Impossible to parse the parameters for dns lookup",e);
		}

		if (srvRecords != null && srvRecords.length > 0)
			return Arrays.asList(srvRecords);
		
		return new ArrayList<SRVRecord>();
	}
	
	/* (non-Javadoc)
	 * @see org.restcomm.ext.javax.sip.dns.DNSLookupPerformer#performNAPTRLookup(java.lang.String, boolean, java.util.Set)
	 */
	public List<NAPTRRecord> performNAPTRLookup(String domain, boolean isSecure, String transport) {
		List<NAPTRRecord> records = new ArrayList<NAPTRRecord>();
		if(logger.isDebugEnabled()) {
			logger.debug("doing NAPTR lookup for domain " + domain + ", isSecure " + isSecure + ", transport " + transport);
		}
		Record[] naptrRecords = null;
		try {
			Lookup lookup = new Lookup(domain, Type.NAPTR);
			Lookup.getDefaultResolver().setTimeout(Duration.ofSeconds(dnsTimeout));
			naptrRecords = lookup.run();
		} catch (TextParseException e) {
			logger.warn("Couldn't parse domain " + domain, e);
		}	
		if(naptrRecords != null) {
			for (Record record : naptrRecords) {
				NAPTRRecord naptrRecord = (NAPTRRecord) record;
				// https://github.com/restcomm/jain-sip.ext/issues/1
				// Compare with uppercase to achieve case-insensitive match
				String service = naptrRecord.getService().toUpperCase();
				if(isSecure) {
					// First, a client resolving a SIPS URI MUST discard any services that
					// do not contain "SIPS" as the protocol in the service field.
					if(service.startsWith(SERVICE_SIPS)) {
						records.add(naptrRecord);
					}
				} else {	
					// The converse is not true, however.
					if(!service.startsWith(SERVICE_SIPS) || 
							(service.startsWith(SERVICE_SIPS) && transport.equalsIgnoreCase(ListeningPoint.TLS))) {
						//A client resolving a SIP URI SHOULD retain records with "SIPS" as the protocol, if the client supports TLS
						if((service.contains(SERVICE_D2U) && transport.equalsIgnoreCase(ListeningPoint.UDP)) ||
								service.contains(SERVICE_D2T) && (transport.equalsIgnoreCase(ListeningPoint.TCP) || transport.equalsIgnoreCase(ListeningPoint.TLS))) {
							// Second, a client MUST discard any service fields that identify
							// a resolution service whose value is not "D2X", for values of X that
							// indicate transport protocols supported by the client.
							records.add(naptrRecord);
						} else if(service.equals(SERVICE_E2U)) {
							// ENUM support
							records.add(naptrRecord);
						}
					} 
				}				
			}
		}			
		return records;
	}

	/* (non-Javadoc)
	 * @see org.restcomm.ext.javax.sip.dns.DNSLookupPerformer#locateHopsForNonNumericAddressWithPort(java.lang.String, int, java.lang.String)
	 */
	public List<Hop> locateHopsForNonNumericAddressWithPort(String host, int port, String transport) {
		List<Hop> priorityQueue = new LinkedList<Hop>();
		
		try {
			if(logger.isDebugEnabled()) {
				logger.debug("doing A lookup for host:port/transport = " + host + ":" + port + "/" + transport);
			}
			Lookup lookup = new Lookup(host, Type.A);
			Lookup.getDefaultResolver().setTimeout(Duration.ofSeconds(dnsTimeout));
			Record[] aRecords = lookup.run();
			if(logger.isDebugEnabled()) {
				logger.debug("A lookup results for host:port/transport = " + host + ":" + port + "/" + transport + " => " + aRecords);
			}
			if(aRecords != null && aRecords.length > 0) {
				for(Record aRecord : aRecords) {
					priorityQueue.add(new HopImpl(((ARecord)aRecord).getAddress().getHostAddress(), port, transport));
				}
			}	
		} catch (TextParseException e) {
			logger.warn("Couldn't parse domain " + host, e);
		}	
		try {
			if(logger.isDebugEnabled()) {
				logger.debug("doing AAAA lookup for host:port/transport = " + host + ":" + port + "/" + transport);
			}
			Lookup lookup = new Lookup(host, Type.AAAA);
			Lookup.getDefaultResolver().setTimeout(Duration.ofSeconds(dnsTimeout));
			Record[] aaaaRecords = lookup.run();
			if(logger.isDebugEnabled()) {
				logger.debug("AAAA lookup results for host:port/transport = " + host + ":" + port + "/" + transport + " => " + aaaaRecords);
			}
			if(aaaaRecords != null && aaaaRecords.length > 0) {
				for(Record aaaaRecord : aaaaRecords) {
					priorityQueue.add(new HopImpl(((AAAARecord)aaaaRecord).getAddress().getHostAddress(), port, transport));
				}
			}			
		} catch (TextParseException e) {
			logger.warn("Couldn't parse domain " + host, e);
		}	
		return priorityQueue;
	}

	// https://code.google.com/p/jain-sip/issues/detail?id=162
	@Override
	public void setDNSTimeout(int timeout) {
		Lookup.getDefaultResolver().setTimeout(Duration.ofSeconds(timeout));
		dnsTimeout = timeout;
		if(logger.isInfoEnabled()) {
			logger.info("DefaultDNSLookupPerformer will be using timeout of " + dnsTimeout + " seconds ");
		}
	}

	@Override
	public int getDNSTimeout() {
		return dnsTimeout;
	}
}
