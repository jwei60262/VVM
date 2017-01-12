package com.att.mobile.android.vvm.protocol.response;


/**
 * Response Class represent a basic response returned by the IMAP4 server.
 * the raw response is parsed by the IMAP4 handler and inserted into a response class.
 * the response is then analyzed by the imap4 handler and its data is sent to the model 
 * and used to continue the commands flow in parallel.
 * more complexes responses may be implemented by sub classes
 * @author ldavid
 *
 */
public class IMAP4Response {

	public static final int RESULT_OK = 0;
	public static final int RESULT_ERROR = 1;
	public static final int CONNECTION_CLOSED = 2;
	
	/**
	 * response result from server
	 */
	protected int result;
	/**
	 * error description
	 */
	protected String description;
	
	public IMAP4Response(){
	}
	
	public IMAP4Response(int result){
		this.result = result;
	}

	public IMAP4Response(int result, String description){
		this.result = result;
		this.description = description;
	}
	
	public int getResult() {
		return result;
	}
	
	public void setResult(int result) {
		this.result = result;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("result = " + result).append("descripiton = ").append(description);
		return result.toString();
	}
	
}
