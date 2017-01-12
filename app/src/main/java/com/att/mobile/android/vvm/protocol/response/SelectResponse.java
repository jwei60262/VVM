package com.att.mobile.android.vvm.protocol.response;

/**
 * Represent a response returned by the Select command.
 * @author ldavid
 *
 */
public class SelectResponse extends IMAP4Response{
	
	protected int exists;
	protected int resents;
	protected int unseen;
	protected long uidValidity;
	protected int uidNext;
	
	public SelectResponse(int result){
		super(result);
	}
	
	public int getExists() {
		return exists;
	}

	public void setExists(int exists) {
		this.exists = exists;
	}

	public int getResents() {
		return resents;
	}

	public void setResents(int resents) {
		this.resents = resents;
	}

	public int getUnseen() {
		return unseen;
	}

	public void setUnseen(int unseen) {
		this.unseen = unseen;
	}

	public long getUidValidity() {
		return uidValidity;
	}

	public void setUidValidity(long uidValidity) {
		this.uidValidity = uidValidity;
	}

	public int getUidNext() {
		return uidNext;
	}

	public void setUidNext(int uidNext) {
		this.uidNext = uidNext;
	}
}
