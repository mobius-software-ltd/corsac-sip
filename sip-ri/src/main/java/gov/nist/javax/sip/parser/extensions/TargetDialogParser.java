package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.extensions.TargetDialog;
import gov.nist.javax.sip.parser.*;

import java.text.ParseException;

/**
 * Parser for Target-Dialog header.
 * @author valeriiamukha
 */
public class TargetDialogParser extends ParametersParser {
	
    public TargetDialogParser(String callId) {
        super(callId);
    }

    protected TargetDialogParser(Lexer lexer) {
        super(lexer);
    }

    @Override
    public SIPHeader parse() throws ParseException {
        TargetDialog targetDialog = new TargetDialog();
        try {
            headerName(TokenTypes.TARGET_DIALOG);
            this.lexer.SPorHT();

            String callId = this.lexer.byteStringNoSemicolon();
            targetDialog.setCallId(callId.trim());

            // Parse parameters (including local and remote tags)
            while (this.lexer.lookAhead(0) == ';') {
                this.lexer.consume(1); // Consume the semicolon
                this.lexer.SPorHT();
               // logger.trace("parameter='{}'", this.lexer.getBuffer()); // Commented out for now
                super.parseNameValueList(targetDialog);
            }

            // Parse remaining characters (should be newline)
            this.lexer.match('\n');

        } catch (ParseException e) {
            throw new ParseException("Error parsing target dialog: " + e.getMessage(), e.getErrorOffset());
        }

        return targetDialog; }
        
        public static void main(String args[]) throws ParseException {
            String tdh[] =
                {   "Target-dialog: kl24ahsd546folnyt2vbak9sad98u23naodiunzds09a3bqw0sdfbsk34poouymnae0043nsed09mfkvc74bd0cuwnms05dknw87hjpobd76f\n",
                    "Target-dialog: 12345th5z8z;local-tag=localzght6-45;remote-tag=remotezght789-337-2\n",
                };

            for (int i = 0; i < tdh.length; i++) {
               TargetDialogParser tp = new TargetDialogParser(tdh[i]);
               TargetDialog t = (TargetDialog) tp.parse();
                System.out.println("Parsing => " + tdh[i]);
                System.out.print("encoded = " + t.encode() + "==> ");
                System.out.println("callId " + t.getCallId() + " local-tag=" + t.getLocalTag()
                        + " remote-tag=" + t.getRemoteTag()) ;

            }
    }
}
            
            
            
            
