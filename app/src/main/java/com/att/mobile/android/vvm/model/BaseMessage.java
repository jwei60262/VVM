package com.att.mobile.android.vvm.model;

import java.util.Vector;

import android.text.format.Time;

/**
 * Contains information about a single message
 */
@Deprecated
public class BaseMessage {
	/**
	 * database id
	 */
	public int messageId;
	
	/**
	 * database id for thread(conversation) containing this message
	 */
	public int conversationId;
	
	public int errorType;

	/**
	 * native id (in case of SMS/MMS - id in system queue)
	 */
	public int nativeId;
	
	/**
	 * message id in the metaswitch backend
	 */
	public String backendIDPrefix;

	/**
	 * message id in the metaswitch backend
	 */
	public long backendIDIndex;
	
	/**
	 * sender as a raw phone number
	 */
	public String sender;
	
	/**
	 * name of sender (extracted from contact list) or null if unknown
	 */
	public String senderName;
	
	/**
	 * list of recipients, as raw phone numbers 
	 */
	public Vector<String> recipients;
	
	/**
	 * list of recipient names, extracted from contact list
	 */
	//public Vector<String> recipientNames;
	
	/**
	 * message type, a MESSAGE_TYPE_XXXXX constant from UMessage
	 */
	public int messageType;
	
	/**
	 * subtype, a MESSAGE_SUBTYPE_XXXX constant from UMessage
	 */
	public int subType;
	
	/**
	 * message status, a MESSAGE_STATUS_XXXXX constant from UMessage
	 */
	public int messageStatus;
	
	/**
	 * sync status, a MESSAGE_SYNCSTATUS_XXXX constant from UMessage
	 */
	public int syncStatus;
	
	/**
	 * true if message is favourited (starred)
	 */
	public boolean favourite;
	
	/**
	 * message text
	 */
	public String text;
	
	/**
	 * URI for thumbnail of person avatar
	 */
	public String thumbnail;
	
	/**
	 * time when message was created
	 */
	public Time created;
	
	/**
	 * time when message was last modified
	 */
	public Time modified;
	
	/**
	 * number of attachments in the message (0 if message has no attachments)
	 */
	public int attachmentCount;
	
	/**
	 * array of attachments for this message, or null if no attachments
	 */
	public Attachment[] attachments;
	
// ========================== variables below are for use by UI ================
	
/**
 
 
	public String getContactName(){
		switch(subType){
		case UMessage.MESSAGE_SUBTYPE_SEND:
		case UMessage.MESSAGE_SUBTYPE_SEND_IN_PROGRESS:
		case UMessage.MESSAGE_SUBTYPE_DRAFT:
		case UMessage.MESSAGE_SUBTYPE_CALL_COMPLETED:
		case UMessage.MESSAGE_SUBTYPE_CALL_UNCOMPLETED:
			if (recipients != null && recipients.size() > 0) {
				return "NAMEOF(" + recipients.get(0) + ")"; 
			}
			break;
		case UMessage.MESSAGE_SUBTYPE_RECEIVED:
		case UMessage.MESSAGE_SUBTYPE_CALL_MISSED:
		case UMessage.MESSAGE_SUBTYPE_RETRIEVE_IN_PROGRESS:
			return (senderName != null) ? senderName : sender; 
		}
		return "";
	}
	public String getContactNumber(){
		String number;
		switch(subType){
		case UMessage.MESSAGE_SUBTYPE_SEND:
		case UMessage.MESSAGE_SUBTYPE_SEND_IN_PROGRESS:
		case UMessage.MESSAGE_SUBTYPE_DRAFT:
		case UMessage.MESSAGE_SUBTYPE_CALL_COMPLETED:
		case UMessage.MESSAGE_SUBTYPE_CALL_UNCOMPLETED:
			if(recipients != null && recipients.size() > 0){
				number = recipients.get(0);
				if(number != null)
					return number;
			}
			break;
		case UMessage.MESSAGE_SUBTYPE_RECEIVED:
		case UMessage.MESSAGE_SUBTYPE_CALL_MISSED:
		case UMessage.MESSAGE_SUBTYPE_RETRIEVE_IN_PROGRESS:
			return sender;
		}
		return "";
	}
	
	public String getSenderName(){
		return senderName;
	}
	
	public String getSender(){
		return sender;
	}
	
	public boolean isIncomingMessage()
	{
		return ((subType == UMessage.MESSAGE_SUBTYPE_RECEIVED) ||
				(subType == UMessage.MESSAGE_SUBTYPE_CALL_MISSED)||
				 subType == UMessage.MESSAGE_SUBTYPE_RETRIEVE_IN_PROGRESS);
	}
	*/
}
