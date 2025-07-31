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

import java.util.List;

import javax.sip.address.Hop;

import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.SRVRecord;

/**
 * Interface to implement for doing the DNS lookups, it uses DNS Java
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public interface DNSLookupPerformer {

	String SERVICE_SIP = "SIP";
	String SERVICE_SIPS = "SIPS";
	String SERVICE_D2U = "D2U";
	String SERVICE_E2U = "E2U+SIP";
	String SERVICE_D2T = "D2T";

	/**
	 * Performing the DNS SRV Lookup for a given Name
	 * @param replacement the replacement for which to perform the SRV lookup
	 * @return an unsorted list of SRV records
	 */
	List<SRVRecord> performSRVLookup(String replacement);

	/**
	 * Performing the DNS NAPTR Lookup for a given domain, whether or not it is secure and the supported transports
	 * @param domain the domain to resolve
	 * @param isSecure whether or not it is secure
	 * @param supportedTransports the transports supported locally
	 * @return an unsorted list of NAPTR Records
	 */
	List<NAPTRRecord> performNAPTRLookup(String domain,
			boolean isSecure, String supportedTransport);

	/**
	 * Perform the A and AAAA lookups for a given host, port and transport
	 * @param host the host
	 * @param port the port
	 * @param transport the transport
	 * @return an unsorted queue of Hops corresponding to the merge of A and AAAA lookup records found
	 */
	List<Hop> locateHopsForNonNumericAddressWithPort(
			String host, int port, String transport);
	
	void setDNSTimeout(int timeout);

	int getDNSTimeout();

}