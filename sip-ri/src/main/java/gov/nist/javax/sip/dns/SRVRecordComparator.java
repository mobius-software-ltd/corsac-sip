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

import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SRVRecordComparator implements Comparator<Record> {

	public int compare(Record o1, Record o2) {
		SRVRecord o1SRVRecord = (SRVRecord) o1;
		SRVRecord o2SRVRecord = (SRVRecord) o2;
		int o1Priority = o1SRVRecord.getPriority();
		int o2Priority = o2SRVRecord.getPriority();
		// the lower priority is the best
		if(o1Priority > o2Priority)
			return 1;
		if(o1Priority < o2Priority)
			return -1;
		
		// if they are the same sort them through weight
		int o1Weight = o1SRVRecord.getWeight();
		int o2Weight = o2SRVRecord.getWeight();
		// the higher weight is the best
		if(o1Weight < o2Weight)
			return 1;
		if(o1Weight > o2Weight)
			return -1;
		// RFC 3263 Section 4.4
		return o1SRVRecord.getTarget().compareTo(o2SRVRecord.getTarget());
	}

}
