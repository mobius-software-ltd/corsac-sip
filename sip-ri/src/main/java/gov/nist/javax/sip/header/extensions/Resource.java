/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 Untied States Code Section 105, works of NIST
* employees are not subject to copyright protection in the United States
* and are considered to be in the public domain.  As a result, a formal
* license is not needed to use the software.
*
* This software is provided by NIST as a service and is expressly
* provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
* Permission to use this software is contingent upon your acceptance
* of the terms of this agreement
*
* .
*
*/
/*******************************************************************************
* Product of NIST/ITL Advanced Networking Technologies Division (ANTD).        *
*******************************************************************************/
package gov.nist.javax.sip.header.extensions;

import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.header.InReplyTo;
import gov.nist.javax.sip.header.SIPObject;

/**
 * The call identifer that goes into a callID header and a in-reply-to header.
 *
 * @author M. Ranganathan   <br/>
 * @version 1.2 $Revision: 1.8 $ $Date: 2010-05-06 14:07:46 $
 * @see CallID
 * @see InReplyTo
 * @since 1.1
 */
public final class Resource extends SIPObject {

    /**
     * Comment for <code>serialVersionUID</code>
     */
    private static final long serialVersionUID = 7314773655675451377L;

    /**
     * namespace field
     */
    protected String namespace;

    /**
     * resource field
     */
    protected String resource;

    /**
     * Default constructor
     */
    public Resource() {
    }

    /**
     * Constructor
     * @param namespace is the namespace of resource.
     * @param resource is the name of resource.
     */
    public Resource(String namespace, String resource) {
        this.namespace = namespace;
        this.resource = resource;
    }

    /**
     * Get the encoded version of this resource.
     * @return String to set
     */
    public String encode() {
        return encode(new StringBuilder()).toString();
    }

    public StringBuilder encode(StringBuilder buffer) {
        buffer.append(namespace).append('.').append(resource);
        return buffer;
    }

    /**
     * Compare two call identifiers for equality.
     * @param other Object to set
     * @return true if the two call identifiers are equals, false
     * otherwise
     */
    public boolean equals(Object other) {
        if (other == null ) return false;
        if (!other.getClass().equals(this.getClass())) {
            return false;
        }
        Resource that = (Resource) other;       
        if (this.namespace == that.namespace)
            return true;
        if ((this.namespace == null && that.namespace != null)
            || (this.namespace != null && that.namespace == null))
            return false;
        if (namespace.compareToIgnoreCase(that.namespace) != 0) {
            return false;
        }
        
        if (this.resource == that.resource)
            return true;
        if ((this.resource == null && that.resource != null)
            || (this.resource != null && that.resource == null))
            return false;
        if (resource.compareToIgnoreCase(that.resource) != 0) {
            return false;
        }
        return true;
    }
    
    @Override
    public int hashCode() {
        if (this.namespace  == null ) {
             throw new UnsupportedOperationException("Hash code called before namespace is set");
        }
        
        final int prime = 31;
		int result = 1;
		result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
		result = prime * result + ((resource == null) ? 0 : resource.hashCode());
		return result;
    }

    /** get the namespace field
     * @return String
     */
    public String getNamespace() {
        return namespace;
    }

    /** get the resource field
     * @return String
     */
    public String getResource() {
        return resource;
    }

    /**
     * Set the namespace member
     * @param namespace String to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /** set the resource field
     * @param resource to set
     * @throws IllegalArgumentException if resource is null
     * token@token
     */
    public void setResource(String resource) throws IllegalArgumentException {
        if (resource == null)
            throw new IllegalArgumentException("NULL!");
       
        this.resource= resource;
    }
}
