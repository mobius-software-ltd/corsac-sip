package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.extensions.TargetDialog;
import gov.nist.javax.sip.parser.ParserTestCase;

public class TargetDialogParserTest extends ParserTestCase {

    @Override
    public void testParser() {
        String[] to = {
                "Target-dialog: f0b40bcc-3485-49e7-ad1a-f1dfad2e39c9@10.5.0.53\n",
                "Target-dialog: f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com\n",
                "Target-dialog: kl24ahsd546folnyt2vbak9sad98u23naodiunzds09a3bqw0sdfbsk34poouymnae0043nsed09mfkvc74bd0cuwnms05dknw87hjpobd76f\n",
                "Target-dialog: 12345th5z8z;local-tag=localzght6-45;remote-tag=remotezght789-337-2\n"
        };

        super.testParser(TargetDialogParser.class, to);
    }
    
    public void testParseCallIdAndParameters() {
        String targetDialogHeader = "Target-dialog: 12345th5z8z;local-tag=localzght6-45;remote-tag=remotezght789-337-2\n";
        try {
            TargetDialogParser parser = new TargetDialogParser(targetDialogHeader);
            SIPHeader header = parser.parse();
            assertNotNull(header);
            assertTrue(header instanceof TargetDialog);
            TargetDialog targetDialog = (TargetDialog) header;

            // Test parsing of callId and parameters
            assertEquals("12345th5z8z", targetDialog.getCallId());
            assertEquals("localzght6-45", targetDialog.getLocalTag());
            assertEquals("remotezght789-337-2", targetDialog.getRemoteTag());
        } catch (Exception e) {
            fail("Exception occurred: " + e.getMessage());
        }
    }

    public void testEncodeBody() throws ParseException {
        // Create a TargetDialog instance with specific values
        TargetDialog targetDialog = new TargetDialog();
        targetDialog.setCallId("12345th5z8z");
        targetDialog.setLocalTag("localzght6-45");
        targetDialog.setRemoteTag("remotezght789-337-2");

        // Encode the TargetDialog instance
        StringBuilder encodedHeader = new StringBuilder();
        targetDialog.encodeBody(encodedHeader);

        // Test if the encoded header matches the expected format
        String expectedHeader = "12345th5z8z;local-tag=localzght6-45;remote-tag=remotezght789-337-2\n";
        assertEquals(expectedHeader.trim().toLowerCase(), encodedHeader.toString().trim().toLowerCase());
    }
}
