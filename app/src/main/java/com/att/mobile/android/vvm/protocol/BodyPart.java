package com.att.mobile.android.vvm.protocol;

/**
 * represent a body part of a VVM message returned by the fetch bodystructure command
 * the class currently contains the string representation of the body part in the fetch response.
 * @author ldavid
 *
 */
public class BodyPart {

	String bodyPartStr;
	String contentType;
	boolean isVttAluReason;
	int duration;
	
	public BodyPart(String bodyPartStr){
		this.bodyPartStr = bodyPartStr;
	}
	
	public String getBodyPartStr() {
		return bodyPartStr;
	}

	public void setBodyPartStr(String bodyPartStr) {
		this.bodyPartStr = bodyPartStr;
	}
	
	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public boolean getIsVttAluReason() {
		return isVttAluReason;
	}

	public void setIsVttAluReason(boolean isVttAluReason) {
		this.isVttAluReason = isVttAluReason;
	}

	public int getDuration() {
		return duration;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}
}