package com.att.mobile.android.vvm.protocol.response;

import com.att.mobile.android.vvm.protocol.BodyStructure;

/**
 * Represent a response returned by the Fetch command.
 * the response can contain response data for the following commands:
 * Fetch bodystructure, Fetch body part (either an audio attachment or another body part like transcription)
 * @author ldavid
 *
 */
public class FetchResponse extends IMAP4Response{
	
	// a body structure holds the body parts 
	protected BodyStructure bodyStructure;

	// the audio file data or transcription
	protected String messageTranscription;
	// if the message if a delivery status notification
	protected boolean isDeliveryStatusMessage = false;
	
	public FetchResponse(){
		super();
	}
	
	public FetchResponse(int result, StringBuffer attachmentBuffer){
		super(result);
		
		if (attachmentBuffer != null)
			this.messageTranscription = attachmentBuffer.toString();
	}
	
	public String getMessageTranscription() {
		return messageTranscription;
	}

	public void setMessageTranscription(String messageTranscription) {
		this.messageTranscription = messageTranscription;
	}
	
	public BodyStructure getBodyStructure() {
		return bodyStructure;
	}

	public void setBodyStructure(BodyStructure bodyStructure) {
		this.bodyStructure = bodyStructure;
	}
	
	public boolean getIsDeliveryStatusMessage() {
		return isDeliveryStatusMessage;
	}

	public void setIsDeliveryStatusMessage(boolean isDeliveryStatusMessage) {
		this.isDeliveryStatusMessage = isDeliveryStatusMessage;
	}
}
