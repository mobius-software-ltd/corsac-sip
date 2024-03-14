
package gov.nist.javax.sip.parser.extensions;

import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.extensions.TargetDialog;
import gov.nist.javax.sip.parser.*;

import java.text.ParseException;
import gov.nist.javax.sip.parser.ParametersParser;

/**
 * Parser for Target-Dialog header.
 * @author ValeriiaMukha
 */
public class TargetDialogParser extends ParametersParser {

    public TargetDialogParser(String targetDialog) {
        super(targetDialog);
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

            String callId;
            char firstChar = this.lexer.lookAhead(0);
            if (firstChar == '<') {
                // Enclosed in <> (quoted string)
                callId = this.lexer.quotedString();
                this.lexer.match('>');
            } else {
                // Not enclosed in <> (SIP URI)
                callId = this.lexer.byteStringNoSemicolon();

                // Check for unexpected characters (excluding whitespace)
                if (callId.indexOf('<') >= 0 || callId.indexOf('>') >= 0) {
                    throw new ParseException("Unexpected characters in Call-Id (<>): " + callId, this.lexer.getPtr());
                }
            }

            targetDialog.setCallId(callId.trim());

            super.parse(targetDialog); // Parse optional parameters (if implemented)
            this.lexer.match('\n');

        } catch (ParseException e) {
            throw new ParseException("Error parsing target dialog: " + e.getMessage(), e.getErrorOffset());
        }

        return targetDialog;
    }
}
