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

        return targetDialog;
    }
}

