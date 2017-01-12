package com.att.mobile.android.vvm.protocol.response;

import java.util.ArrayList;

import com.att.mobile.android.vvm.model.Message;

/**
 * Represent a response returned by the Fetch headers command.
 * fetch header command retires all messages header in the inbox.
 * the header of each message in the server reply in then parsed into message objects and put into this class messages list.
 * @author ldavid
 *
 */
public class FetchHeadersResponse extends IMAP4Response  {

	// a list of message objects containing the requested header fields
	protected ArrayList<Message> messagesList;
	
	public FetchHeadersResponse(int result) {
		super(result);
	}
	
	public FetchHeadersResponse() {
		super();
	}
	
	public FetchHeadersResponse(int result, Message message) {
		super(result);
	}
}
