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

import java.util.Comparator;

import org.xbill.DNS.NAPTRRecord;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class NAPTRRecordComparator implements Comparator<NAPTRRecord> {

	public int compare(NAPTRRecord o1, NAPTRRecord o2) {
		int o1Order = o1.getOrder();
		int o2Order = o2.getOrder();
		
		//Issue http://code.google.com/p/mobicents/issues/detail?id=3143
		//Check record preference
		int o1preference = o1.getPreference();
		int o2preference = o2.getPreference();
		
		if (o1Order == o2Order){
			if (o1preference > o2preference)
				return 1;
			if (o1preference < o2preference)
				return -1;
			return 0;
		}
		
		// The lower order is the best
		if(o1Order > o2Order)
			return 1;
		if(o1Order < o2Order)
			return -1;
		return 0;
	}

}
