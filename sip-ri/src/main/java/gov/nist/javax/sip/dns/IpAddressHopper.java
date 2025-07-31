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

import java.util.concurrent.atomic.AtomicBoolean;

import javax.sip.address.Hop;

import gov.nist.javax.sip.stack.HopImpl;
import gov.nist.javax.sip.util.Inet6Util;

/**
 * Simple hopper that provides a single hop for a specific IP address.
 *
 * @author hrosa (henrique.rosa@telestax.com) on 2019-04-14
 */
public class IpAddressHopper implements Hopper {

    private final int port;
    private final String ipAddress;
    private final String transport;
    private final AtomicBoolean hopped;

    public IpAddressHopper(int port, String ipAddress, String transport) {
        if (!Inet6Util.isValidIPV4Address(ipAddress) && !Inet6Util.isValidIP6Address(ipAddress)) {
            throw new IllegalArgumentException(ipAddress + " is not a valid IP Address");
        }
        this.port = port;
        this.ipAddress = ipAddress;
        this.transport = transport;
        this.hopped = new AtomicBoolean(false);
    }

    @Override
    public Hop hop() {
        if (this.hopped.compareAndSet(false, true)) {
            return new HopImpl(this.ipAddress, this.port, this.transport);
        }
        return null;
    }

}
