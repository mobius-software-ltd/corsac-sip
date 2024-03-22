package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.extensions.TargetDialog;
import gov.nist.javax.sip.parser.ParserTestCase;

public class TargetDialogParserTest extends ParserTestCase {

    @Override
    public void testParser() {
        String[] targetDialogHeaders = {
                "Target-dialog: f0b40bcc-3485-49e7-ad1a-f1dfad2e39c9@10.5.0.53\n",
                "Target-dialog: f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com\n",
                "Target-dialog: kl24ahsd546folnyt2vbak9sad98u23naodiunzds09a3bqw0sdfbsk34poouymnae0043nsed09mfkvc74bd0cuwnms05dknw87hjpobd76f\n",
                "Target-dialog: 12345th5z8z;local-tag=tozght6-45;remote-tag=fromzght789-337-2\n"
        };

        super.testParser(TargetDialogParser.class, targetDialogHeaders);

        // Additional test for toString method
        for (String targetDialogHeader : targetDialogHeaders) {
            try {
                TargetDialogParser parser = new TargetDialogParser(targetDialogHeader);
                SIPHeader header = parser.parse();
                assertNotNull(header);
                assertTrue(header instanceof TargetDialog);
                String toString = header.toString();
                assertNotNull(toString);
                assertFalse(toString.isEmpty());
                // Assert if toString doesn't throw any exception
            } catch (Exception e) {
                fail("Exception occurred: " + e.getMessage());
            }
        }
    }
}

