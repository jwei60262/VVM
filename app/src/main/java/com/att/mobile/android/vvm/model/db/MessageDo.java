package com.att.mobile.android.vvm.model.db;

/**
 * Data Object that can hold the inbox messages data as in inbox table on database.
 * @author snirc
 *
 */
public class MessageDo {
	
	private long id;
	private long uid;
	private String time;
	private String phoneNumber;
	private String transcription;
	private String fileName;
	private String savedState;
	private String forwardState;
	private String readState;
	private boolean isCheckedForDelete;

	public boolean isCheckedForDelete() {
		return isCheckedForDelete;
	}
	public void setCheckedForDelete(boolean isCheckedForDelete) {
		this.isCheckedForDelete = isCheckedForDelete;
	}
	/**
	 * @param time the time to set
	 */
	public void setTime(String time) {
		this.time = time;
	}
	/**
	 * @return the time
	 */
	public String getTime() {
		return time;
	}
	/**
	 * @param phoneNumber the phoneNumber to set
	 */
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	/**
	 * @return the phoneNumber
	 */
	public String getPhoneNumber() {
		return phoneNumber;
	}
	/**
	 * @param transcription the transcription to set
	 */
	public void setTranscription(String transcription) {
		this.transcription = transcription;
	}
	/**
	 * @return the transcription
	 */
	public String getTranscription() {
		return transcription;
	}
	/**
	 * @param fileName the fileName to set
	 */
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	/**
	 * @return the fileName
	 */
	public String getFileName() {
		return fileName;
	}
	/**
	 * @param savedState the savedState to set
	 */
	public void setSavedState(String savedState) {
		this.savedState = savedState;
	}
	/**
	 * @return the savedState
	 */
	public String getSavedState() {
		return savedState;
	}
	/**
	 * @param forwardState the forwardState to set
	 */
	public void setForwardState(String forwardState) {
		this.forwardState = forwardState;
	}
	/**
	 * @return the forwardState
	 */
	public String getForwardState() {
		return forwardState;
	}
	/**
	 * @param readState the readState to set
	 */
	public void setReadState(String readState) {
		this.readState = readState;
	}
	/**
	 * @return the readState
	 */
	public String getReadState() {
		return readState;
	}
	/**
	 * @param uid the uid to set
	 */
	public void setUid(long uid) {
		this.uid = uid;
	}
	/**
	 * @return the uid
	 */
	public long getUid() {
		return uid;
	}
	/**
	 * @param id the id to set
	 */
	public void setId(long id) {
		this.id = id;
	}
	/**
	 * @return the id
	 */
	public long getId() {
		return id;
	}
	
}
