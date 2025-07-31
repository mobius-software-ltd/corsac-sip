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

import gov.nist.javax.sip.stack.HopImpl;
import gov.nist.javax.sip.util.Inet6Util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;

import javax.sip.address.Hop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Load Balancer for DNS SRV records that relies on javaDNS to handle discovery and caching of DNS SRV records.
 * <p>
 * The balancing algorithm is described in <a href="https://tools.ietf.org/html/rfc2782">RFC2782</a>, pages 3-4 as part
 * of the Weight field description.
 * <p>
 *
 * @author hrosa (henrique.rosa@telestax.com) on 2019-03-28
 */
public class DnsSrvHopper implements Hopper {

    private static final Logger log = LogManager.getLogger(DnsSrvHopper.class);

    private static final SrvComparator SRV_COMPARATOR = new SrvComparator();
    private static final List<SRVRecord> NO_SRV_RECORDS = Collections.emptyList();

    private final DNSLookupPerformer dnsLookup;
    private final Random random = new Random();
    private final String query;
    private final String transport;

    // runtime state
    private int priority;
    private Hopper aRecordHopper;

    public DnsSrvHopper(String query, DNSLookupPerformer dnsLookup) {
        if (!query.endsWith(".")) {
            query = query.concat(".");
        }

        final int transportStart = query.lastIndexOf("_") + 1;
        final int transportEnd = query.indexOf(".", transportStart);

        this.query = query;
        this.transport = query.substring(transportStart, transportEnd);
        this.dnsLookup = dnsLookup;

        this.priority = -1;
    }

    public DnsSrvHopper(String service, String transport, String host, DNSLookupPerformer dnsLookup) {
        this("_" + service.toLowerCase() + "._" + transport.toLowerCase() + "." + host, dnsLookup);
    }

    @Override
    public Hop hop() {
        if (this.aRecordHopper == null) {
            // Look up records for given query
            // Needs to be triggered every time because caching and expiration are handled by javaDNS under hood
            final List<SRVRecord> records = lookup(this.query);

            // Remove priorities that we already hopped over in previous iterations
            prune(this.priority, records);

            if (records.isEmpty()) {
                // No more hops available
                log.debug("No more hops are available!");
                return null;
            } else {
                // Retrieve highest priority available
                this.priority = records.get(0).getPriority();

                // Fetch all available records assigned to highest available priority
                // And compute total weight
                final LinkedHashMap<SRVRecord, Integer> eligibleRecords = new LinkedHashMap<>(records.size());
                int totalWeight = 0;

                while (!records.isEmpty() && records.get(0).getPriority() == this.priority) {
                    final SRVRecord record = records.remove(0);
                    totalWeight += record.getWeight();
                    // Assign running-sum to each record
                    eligibleRecords.put(record, totalWeight);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Query " + query + " is balancing records with priority " + priority + ": " + Arrays.toString(eligibleRecords.values().toArray()));
                }

                // Need to balance between multiple records
                // Calculate a random number between 0 and totalWeight (inclusive)
                final int threshold = random.nextInt(totalWeight + 1);

                /*
                 * Select the record whose running sum value is the first
                 * in the selected order which is greater than or equal to
                 * the random number selected
                 */
                final Iterator<Map.Entry<SRVRecord, Integer>> iterator = eligibleRecords.entrySet().iterator();
                while (iterator.hasNext()) {
                    final Map.Entry<SRVRecord, Integer> entry = iterator.next();
                    if (entry.getValue() >= threshold) {
                        final SRVRecord elected = entry.getKey();

                        if (log.isDebugEnabled()) {
                            log.debug("Query " + query + " elected record " + elected + " after pruning with threshold " + threshold);
                        }

                        final String address = elected.getTarget().toString(true);
                        if (isIpAddress(address)) {
                            // SRV record provided IP address directly
                            return new HopImpl(address, elected.getPort(), transport);
                        } else {
                            // Build a new hopper to hop through A records
                            this.aRecordHopper = new DnsAHopper(address, elected.getPort(), transport, this.dnsLookup);
                            return hop();
                        }
                    }
                }

                // Somehow no record was found
                return null;
            }
        } else {
            // Keep trying A records top-down
            final Hop nextHop = this.aRecordHopper.hop();
            if (nextHop!=null) {
                // A record found. Return it and keep hopper in case this one is unreachable.
                if (log.isDebugEnabled()) {
                    log.debug("Query " + query + " found next available A record " + nextHop);
                }
                return nextHop;
            } else {
                // No more A records for current hop!
                if (log.isDebugEnabled()) {
                    log.debug("Query " + query + " exhausted all A records for current hop. Will skip to next SRV record.");
                }

                // Clean hopper to prevent more recursive calls to this branch
                // and force looking for next SRV record
                this.aRecordHopper = null;

                // Start hopping through next SRV record (if any)
                return hop();
            }
        }
    }

    private List<SRVRecord> lookup(String pattern) {
        // Perform lookup of SRV records
        final List<SRVRecord> records = dnsLookup.performSRVLookup(pattern);
        if (records.isEmpty()) {
            // No records were found!
            if (log.isDebugEnabled()) {
                log.debug("No SRV records were found for query " + pattern);
            }
            return NO_SRV_RECORDS;
        } else {
            // Migrate records to a correctly typed collection
            final List<SRVRecord> srvRecords = new ArrayList<>(records.size());
            for (Record record : records) {
                srvRecords.add((SRVRecord) record);
            }

            if (log.isDebugEnabled()) {
                log.debug("Query " + pattern + " returned the following SRV records: " + Arrays.toString(srvRecords.toArray()));
            }

            // Order found records by priority and weight
            srvRecords.sort(SRV_COMPARATOR);

            if (log.isDebugEnabled()) {
                log.debug("Query " + pattern + " will receive record list ordered as follows: " + Arrays.toString(srvRecords.toArray()));
            }

            return srvRecords;
        }
    }

    private void prune(int priorityHigherThan, List<SRVRecord> records) {
        final Iterator<SRVRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            final SRVRecord record = iterator.next();
            if (record.getPriority() <= priorityHigherThan) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private boolean isIpAddress(String address) {
        return Inet6Util.isValidIPV4Address(address) || Inet6Util.isValidIP6Address(address);
    }

    private static class SrvComparator implements Comparator<SRVRecord> {

        @Override
        public int compare(SRVRecord o1, SRVRecord o2) {
            // Wins the record with lower priority
            final int priorityDiff = o1.getPriority() - o2.getPriority();
            if (priorityDiff != 0) {
                return priorityDiff;
            }

            // If both records share same priority, then we need to untie them
            // For balancing purposes, records with zero weight MUST be placed at beginning to greatly lower chances of them being elected
            final int weight1 = o1.getWeight();
            final int weight2 = o1.getWeight();

            if (weight1 == 0) {
                return 1;
            }

            if (weight2 == 0) {
                return -1;
            }
            
            return o1.getTarget().compareTo(o2.getTarget());
            // Otherwise distribute records randomly
            //return new Random().nextBoolean() ? 1 : -1;
        }

    }

}
