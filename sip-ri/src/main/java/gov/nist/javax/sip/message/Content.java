package gov.nist.javax.sip.message;

import java.util.Iterator;

import javax.sip.header.ContentDispositionHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.Header;

public interface Content {

  public abstract void setContent(byte[] content);

  public abstract byte[] getContent();

  public abstract byte[] getEncodedHeaders();

  public abstract ContentTypeHeader getContentTypeHeader();

  public abstract ContentDispositionHeader getContentDispositionHeader();

  public abstract Iterator<Header> getExtensionHeaders();   
}
