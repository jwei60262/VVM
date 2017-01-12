package com.att.mobile.android.vvm.protocol.response;

/**
 * Represent a response returned by the GetQuota command.
 * @author nslesuratin
 *
 */
public class GetQuotaResponse extends IMAP4Response{
	
	protected int exists;
	protected int quota;
	
	public GetQuotaResponse(int result){
		super(result);
	}
	
	public int getExists() {
		return exists;
	}

	public void setExists(int exists) {
		this.exists = exists;
	}

	public int getQuota() {
		return quota;
	}

	public void setQuota(int quota) {
		this.quota = quota;
	}

}
