package com.att.mobile.android.vvm.protocol.response;

import java.util.HashMap;

/**
 * Represent a response returned by the get metadata command.
 * the response contains a variable and the value was requested from the server 
 * @author ldavid
 */
public class MetaDataResponse extends IMAP4Response{
	
	// the audio file data or transcription
	protected HashMap<String, String> variables;
	
	public MetaDataResponse(){
		super();
		variables = new HashMap<String, String>();
	}
	
	public HashMap<String, String> getVariables() {
		return variables;
	}

	public void addVariable(String variable, String value) {
		variables.put(variable, value);
	}
	
	public void addVariablesRange(HashMap<String,String> variables){
		this.variables.putAll(variables);
	}
}