package test.unit.gov.nist.javax.sip.parser;

import gov.nist.javax.sip.parser.RecordRouteParser;
import gov.nist.javax.sip.parser.RouteParser;

public class RecordRouteParserTest extends ParserTestCase {

    public void testParser() {
        // TODO Auto-generated method stub
        String rou[] = {
                "Record-Route: <sip:bob@biloxi.com;maddr=10.1.1.1>,"+
                            "<sip:bob@biloxi.com;maddr=10.2.1.1>\n",

                "Record-Route: <sip:UserB@there.com;maddr=ss2.wcom.com>\n",

                "Record-Route: <sip:+1-650-555-2222@iftgw.there.com;"+
                            "maddr=ss1.wcom.com>\n",

                "Record-Route: <sip:UserB@there.com;maddr=ss2.wcom.com>,"+
                            "<sip:UserB@there.com;maddr=ss1.wcom.com>\n"  ,
                "Record-Route: <sip:3Zqkv5bGjusip%3A%2B3519116786244%40siplab.domain.com@scscf.ojt4.trial.net:7070;maddr=213.0.115.163;lr>\n",
                "Record-Route: <sip:[fe80:0:0:0:20c:29ff:febc:37c7]:5065;transport=tcp;lr;node_host=fe80:0:0:0:20c:29ff:fe7d:7f9c%252;node_port=5060;version=0>\n"        
        };


        super.testParser(RecordRouteParser.class,rou);
    }

}

