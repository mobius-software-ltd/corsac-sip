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
package gov.nist.javax.sip.util;

import gov.nist.javax.sip.Utils;

/**
 * @author hrosa (henrique.rosa@telestax.com) on 2019-06-06
 */
public class UtilsExtension extends Utils {

    public static boolean isIpAddress(String address) {
        return Inet6Util.isValidIPV4Address(address) || Inet6Util.isValidIP6Address(address);
    }

}
