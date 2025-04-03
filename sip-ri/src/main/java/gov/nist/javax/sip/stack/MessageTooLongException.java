package gov.nist.javax.sip.stack;

import gov.nist.core.Separators;

/** The MessageTooLongException defines a exception for message that extends the sending limit defined for protocol.
 *
 * @author yulian.oifa
 * @version 1.0
 */
public class MessageTooLongException extends Exception {

   
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Creates new MessageTooLongException
     */
    public MessageTooLongException() {
         super();
    }

    /** Constructs a new MessageTooLongException with the message you specify.
     * @param message a String specifying the text of the exception message
     */    
    public MessageTooLongException(String message){
         super(message);
    }
    
    /** Constructs a new MessageTooLongException when the Codelet needs to throw an 
     * exception and include a message about another exception that interfered
     * with its normal operation.
     * @param message a String specifying the text of the exception message
     * @param rootCause the Throwable exception that interfered with the 
     * Codelet's normal operation, making this Codelet exception necessary
     */    
    public MessageTooLongException(String message,
    Throwable rootCause){
        super(rootCause.getMessage()+ Separators.SEMICOLON +message);
    }
    
    /** Constructs a new MessageTooLongException as a result of a system exception and uses
     * the localized system exception message.
     * @param rootCause the system exception that makes this MessageTooLongException necessary
     */    
    public MessageTooLongException(Throwable rootCause){
        super(rootCause.getLocalizedMessage());
    }
    
    /** Returns the Throwable system exception that makes this MessageTooLongException necessary.
     * @return Throwable
     */    
    public Throwable getRootCause(){
        return fillInStackTrace(); 
    }
    
}
