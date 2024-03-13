package gov.nist.javax.sip.parser.extensions;

import java.text.ParseException;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.extensions.*;
import gov.nist.javax.sip.parser.*;

/**
 * ReferredBy Header parser.
 *
 * <a href="{@docRoot}/uncopyright.html">This code is in the public domain.</a>
 *
 * Based on JAIN ReferToParser
 *
 */
public class TargetDialogParser extends AddressParametersParser {

    /**
     * Creates new ToParser
     * @param referBy String to set
     */
    public TargetDialogParser(String targetDialog) {
        super(targetDialog);
    }

    protected TargetDialogParser(Lexer lexer) {
        super(lexer);
    }

    public SIPHeader parse() throws ParseException {
        headerName(TokenTypes.TARGET_DIALOG);
        TargetDialog target = new TargetDialog();
        super.parse(target);
        this.lexer.match('\n');
        return target;
    }

    public static void main(String args[]) throws ParseException {
        String to[] = {
            "Target-Dialog: <sip:dave@denver.example.org?" +
                    "Replaces=12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994>\n",
            "Target-Dialog: <sip:+1-650-555-2222@ss1.wcom.com;user=phone>;tag=5617\n",
            "Target-Dialog: T. A. Watson <sip:watson@bell-telephone.com>\n",
            "Target-Dialog: LittleGuy <sip:UserB@there.com>\n",
            "Target-Dialog: sip:mranga@120.6.55.9\n",
            "Target-Dialog: sip:mranga@129.6.55.9 ; tag=696928473514.129.6.55.9\n"
        };

        for (int i = 0; i < to.length; i++) {
            TargetDialogParser tp = new TargetDialogParser(to[i]);
            TargetDialog t = (TargetDialog) tp.parse();
            System.out.println("encoded = " + t.encode());

        }
    }
} 


    