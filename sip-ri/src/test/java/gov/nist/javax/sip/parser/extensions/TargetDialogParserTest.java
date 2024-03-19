package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.parser.ParserTestCase;

public class TargetDialogParserTest extends ParserTestCase {

    @Override
    public void testParser() {
        String[] targetDialogHeaders = {
                "Target-dialog: f0b40bcc-3485-49e7-ad1a-f1dfad2e39c9@10.5.0.53\r\n",
                "Target-dialog: f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com\r\n",
                "Target-dialog: 1@10.0.0.1\r\n",
                "Target-dialog: kl24ahsd546folnyt2vbak9sad98u23naodiunzds09a3bqw0sdfbsk34poouymnae0043nsed09mfkvc74bd0cuwnms05dknw87hjpobd76f\r\n",
                "Target-dialog: 281794\r\n",
        		//local tag
                "Target-dialog: f0b40bcc-3485-49e7-ad1a-f1dfad2e39c9@10.5.0.53\r\n",
                //remote tag 
                "Target-dialog: f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com\r\n",
                // local tag 
                "Target-dialog: kl24ahsd546folnyt2vbak9sad98u23naodiunzds09a3bqw0sdfbsk34poouymnae0043nsed09mfkvc74bd0cuwnms05dknw87hjpobd76f@example.com\r\n" 
        };

        super.testParser(TargetDialogParser.class, targetDialogHeaders);
    }
}

