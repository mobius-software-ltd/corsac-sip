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

/**
 * Performs lookup of A records for a given non-numeric host address and then hops them top-down.
 *
 * @author hrosa (henrique.rosa@telestax.com) on 2019-06-06
 */
public class DnsAHopper implements Hopper {

    private final List<Hop> hops;

    public DnsAHopper(String host, int port, String transport, DNSLookupPerformer dnsLookupPerformer) {
        this.hops = dnsLookupPerformer.locateHopsForNonNumericAddressWithPort(host, port, transport);
    }

    @Override
    public Hop hop() {
    	if(hops == null || hops.size()==0)
    		return null;
    	
        return hops.remove(0);
    }
}