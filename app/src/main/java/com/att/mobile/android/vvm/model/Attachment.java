package com.att.mobile.android.vvm.model;

/**
 * structure describing a single attachment of a message
 */
@Deprecated
public class Attachment 
{
	/** the message ID of this attachment */
	public int messageId;
	
	/** MIME type of attachment (example: text/plain, image/jpeg, audio/mp3) */
	public String mimeType;
	
	/** the URI for getting the attachment contents */
	public String mediaUri;
	
	/** the attachment file name (unique id for attachment) */
	public String fileName;
	
	/** attachment type */
	public int attachmentType;
}
