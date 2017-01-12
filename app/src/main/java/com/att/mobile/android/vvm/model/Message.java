package com.att.mobile.android.vvm.model;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;
import com.att.mobile.android.vvm.model.db.ModelManager;


/**
 * This object represents an VVM Message Header and content
 */
public class Message implements  Comparable<Message>, Parcelable
{
	private static final String TAG = "Message";

	public Uri getContactImageUri() {
		return contactImageUri;
	}

	public void setContactImageUri(Uri contactImageUri) {
		this.contactImageUri = contactImageUri;
	}

	/**
	 * Holds prefix and postfix for message's received transcription from the server.
	 * "A voicemail from 97239766012 at 08:05:15 AM 04/27/11 could not be processed to text."
	 */
	public static interface TranscriptionFixes
	{
		// this is what we get on demo lab
		public static final String NO_TRANSCRIPTION_PREFIX = "Audio message from";
		public static final String NO_TRANSCRIPTION_POSTFIX = "cannot be processed into text.";
		
		// this is what we get on redmond lab
		public static final String NO_TRANSCRIPTION_PREFIX_1 = "A voicemail from";
		public static final String NO_TRANSCRIPTION_POSTFIX_1 = "could not be processed to text.";
		
		 
	}
	
	/**
	 * Holds bitwise OR possible states for message's saved state.
	 */
	public static interface SavedStates
	{
		public static final int TUI = 1;
		public static final int INTERNAL_STORAGE = 2;
		public static final int INTERNAL_STORAGE_AS_SAVED = 4;
		public static final int ERROR = 8;
	}
	
	/**
	 * Holds possible states for message's read state.
	 */
	public static interface ReadDeletedState
	{
		public static final int UNREAD = 0;
		public static final int READ = 1;
		public static final int DELETED = 2;
	}
	
	/**
	 * Holds possible states for Watson transcription.
	 */
	public static interface WatsonTranscriptionState
	{
		public static final int DEFAULT = 0;
		public static final int WAIT_FOR_TRANSCRIPTION = 1;
		public static final int RETRY = 2;
		public static final int TRANSCRIPTION_RECEIVED = 3;
		public static final int TRANSCRIPTION_FAILED = 4;
		public static final int WAIT_FOR_RETRY = 5;
		public static final int PENDING_WAIT_FOR_RETRY = 6;
		public static final int PENDING_RETRY = 7;
	}

	public static interface UrgentState
	{
		public static final int NORMAL = 0;
		public static final int URGENT = 1;
	}
	
	public static interface WAS_DOWNLOADED_STATE
	{
		public static final int NO = 0;
		public static final int YES = 1;
	}
	
	public static interface CAN_OVERWRITE_STATE
	{
		public static final int NO = 0;
		public static final int YES = 1;
	}
	
	/** Proporties constans*/
	public static final byte PRIORITY_NONE   = 0;
	public static final byte PRIORITY_HIGH = 1;
	public static final byte PRIORITY_LOW    = -1;

	/** Maximum chunk size*/
	public static final int MAX_CHUNK_SIZE = 1024;

	public static final String FOLDER_INBOX = "INBOX";
	
	private static final String SERVER_DATE_TIME_FORMAT = "E, dd MMM yyyy HH:mm:ss Z";

	/**
	 * The UID of the message on the server - primery Key
	 */
	private long uid;
	/**
	 * The display name of the message originator from from header
	 */
	private String senderDisplayName = null;

	/**
	 *  Date and time of the Encor Message format
	 *  eg. Apr 30, 2008
	 */
	private long dateMillis;

	/**
	 * is message had been read or not (equal to saved in TUI)
	 */
	private boolean readStatus = false;
	
	/**
	 * prioritized as urgent or normal
	 */
	private int urgentStatus = UrgentState.NORMAL;
	/**
	 * is this message is a delivery status notification
	 */
	private boolean isDeliveryStatus = false;
	
	/**
	 * is message marked as private
	 */
	private boolean privateStatus = false;
	
	/**
	 * is message marked as TUISkipped in the server
	 */
	private boolean tuiskipped = false;
	
	private int savedState;
	private Bitmap senderBitmap = null;
	/**
	 * voice message transcription
	 */
	private String transcription;
	/**
	 * maintain references to contacts, use lookup keys instead of the traditional row ids.
	 * acquire a lookup key from the contact itself
	 * it is a column on the ContactsContract.Contacts table.
	 * Once you have a lookup key, you can construct a URI in this way
	 */
	private String contactLookupKey = null;

	private Uri contactImageUri = null;



	/**
	 * file location in the application storage
	 */
	private volatile String fileName = null;
	 @Override
	public int hashCode() {
		int result = 17;
		result = (int) (31 * result + uid);
		return result;
	}
	/**
	 * 
	 */
	private String senderPhoneNumber = null;
	/**
	 * 
	 */
	private long previousUid = -1;
	
	
	/**
	 * the message row id in db
	 */
	private long rowId; 
	
	/**
	 * The user defined label for the phone number. 
	 */
	private String phoneNumberLabel;
	
	private int phoneNumberType;
	
	/**
	 * constructs an empty VVM Message.
	 * UID is set to -1.
	 *
	 */
	public Message(){
		this.uid             = -1;
		this.readStatus           = false;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Message other = (Message) obj;
		if (uid != other.uid)
			return false;
		return true;
	}

	/**
	 * Return message's unique id.
	 * @return  message's uid
	 */
	public long getUid() {
		return uid;
	}

	/**
	 * Set the message uid
	 * @param uid - requested uid.
	 */
	public void setUid(long uid) {
		this.uid = uid;
	}



	/**
	 * @param date the date to set
	 */
	public void setDate(String dateStr) {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(SERVER_DATE_TIME_FORMAT, Locale.US);
		try {
			this.dateMillis = simpleDateFormat.parse(dateStr.trim()).getTime();
		} catch (ParseException e) {
			Logger.e(TAG, e.getMessage(), e);;
		}
	}
	
	/**
	 * set the date in long without parsing
	 * @author istelman
	 * @param date
	 */
	public void setDateLong(long date){
		this.dateMillis = date;
	}


	/**
	 *  Return value of message read (heard) state
	 * @return boolean - true for read message, false for unseen message.
	 */
	public boolean isRead() {
		return readStatus;
	}

	/**
	 * Setter for read state.
	 * @param read
	 */
	public void setRead(boolean read) {
		this.readStatus = read;
	}

	public int getSavedIndex() {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isMessageLoaded() {
		// TODO Auto-generated method stub
		return false;
	}

	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append("Message[uid=" + uid);
		result.append(",read=" + readStatus);
		result.append(",date=" + dateMillis);
		result.append(",fileName=" + fileName);

		return result.toString();
	}

	public String getFileName() {
		return fileName;
	}
	
	/**
	 * Returns message's attachment (file) full path, according to where
	 * it is being stored (internal / external application's storage).
	 * 
	 * @return (String) message's file full path according to where it is being stored,
	 * 					or null in case message's file doesn't exist yet in the application.
	 */
	public String getFileFullPath(Context context)
	{
		//holds file's full path
		String fileFullPath = null;
		
		//in case message's file exist in application's internal storage
		if(savedState == SavedStates.INTERNAL_STORAGE ||
				savedState == SavedStates.INTERNAL_STORAGE_AS_SAVED)
		{
			//returns file's full path from application's internal storage
			fileFullPath = new StringBuilder(context.getFilesDir().getPath()).append(File.separator).append(fileName).toString();
			Logger.d(TAG, "Message.getFileFullPath() - file's full path is: " + fileFullPath);
			return fileFullPath;
		}
		
		//in case message's file exist in application's external storage
//		if((savedState & SavedStates.EXTERNAL_STORAGE) == SavedStates.EXTERNAL_STORAGE)
//		{
//			//returns file's full path from application's external storage
//			fileFullPath = new StringBuilder(context.getExternalFilesDir(null).getPath()).append(File.separator).append(fileName).toString();
//			Logger.d(TAG, "Message.getFileFullPath() - file's full path is: " + fileFullPath);
//			return fileFullPath;
//		}
		
		//message's file doesn't exist yet in the application
		return null;
	}
	
//	public boolean isSavedToExternalStorage(){
//		return (savedState & SavedStates.EXTERNAL_STORAGE) == SavedStates.EXTERNAL_STORAGE;
//	}
	
	/**
	 * Returns message's attachment (file) according to where
	 * it is being stored (internal / external application's storage).
	 * 
	 * @return (File) message's file according to where it is being stored,
	 * 				  or null in case message's file doesn't exist yet in the application.
	 */
	public File getFile(Context context)
	{
		//in case message's file exist in application's internal storage
		if(savedState == SavedStates.INTERNAL_STORAGE ||
				savedState == SavedStates.INTERNAL_STORAGE_AS_SAVED)
		{
			return VvmFileUtils.getInternalFile(context, fileName);
		}
		
		//in case message's file exist in application's external storage
//		if((savedState & SavedStates.EXTERNAL_STORAGE) == SavedStates.EXTERNAL_STORAGE)
//		{
//			return FileUtils.getExternalFile(context, fileName);
//		}
		
		//message's file doesn't exist yet in the application
		return null;
	}

	public void setFileName(String fileName) {
		// TODO move this synchronized block into the FTEOPeration
		synchronized (this) {
			this.fileName = fileName;
			notifyAll();
		}
	}

	public String getTranscription() {
		return transcription;
	}

        //when transcription unavailable the message look like
	    //"Audio message from 6172312755 at 01:07:40 AM 11/29/10 cannot be processed into text."
  	    //the product decide that he want an empty transcription what it unavailable  
		public void setTranscription(Context context, String transcription) {
			if (transcription == null){
				this.transcription = ModelManager.NO_TRANSCRIPTION_STRING;
			}else if (transcription.startsWith("Audio message from") && transcription.endsWith("cannot be processed into text."))
				this.transcription = context.getString(R.string.trascriptionErrorText);
			else{
				this.transcription = transcription;
			}
	}

	

	public long getDateMillis() {
		return dateMillis;
	}

	
	public long getPreviousUid() {
		return previousUid;
	}

	public void setPreviousUid(long previousUid) {
		this.previousUid = previousUid;
	}
	

	
	/**
	 * @return the rowId
	 */
	public long getRowId() {
		return rowId;
	}

	/**
	 * @param rowId the rowId to set
	 */
	public void setRowId(long rowId) {
		this.rowId = rowId;
	}

	@Override
	public int compareTo(Message another) {
		
		if (dateMillis < another.getDateMillis())
			return 1;
		else if (dateMillis > another.getDateMillis())
			return -1;
		else
			return 0;
	}

	/**
	 * @return the saved
	 */
	public int getSavedState()
	{
		return savedState;
	}

	/**
	 * @param savedState the saved to set
	 */
	public void setSavedState(int savedState) {
		this.savedState = savedState;
	}

	/**
	 * @return the senderBitmap
	 */
	public Bitmap getSenderBitmap() {
		return senderBitmap;
	}

	/**
	 * @param senderBitmap the senderBitmap to set
	 */
	public void setSenderBitmap(Bitmap senderBitmap) {
		this.senderBitmap = senderBitmap;
	}

	
	public String getSenderPhoneNumber() {
		return senderPhoneNumber;
	}
	
	
	public void setSenderPhoneNumber(String phoneString) {
		this.senderPhoneNumber = phoneString;
	}
	/**
	 * @return the senderDisplayName
	 */
	public String getSenderDisplayName() {
		return senderDisplayName;
	}

	/**
	 * @param senderServerDisplayName the senderDisplayName to set
	 */
	public void setSenderDisplayName(String senderServerDisplayName) {
		this.senderDisplayName = senderServerDisplayName;
	}
	
	public String getPhoneNumberLabel() {
		return phoneNumberLabel;
	}

	public void setPhoneNumberLabel(String phoneNumberLabel) {
		this.phoneNumberLabel = phoneNumberLabel;
	}
	
	public int getPhoneNumberType() {
		return phoneNumberType;
	}

	public void setPhoneNumberType(int phoneNumberType) {
		this.phoneNumberType = phoneNumberType;
	}
	
	public String getContactLookupKey() {
		return contactLookupKey;
	}

	public void setContactLookupKey(String contactLookupKey) {
		this.contactLookupKey = contactLookupKey;
	}
	
	public boolean isTuiskipped() {
		return tuiskipped;
	}

	public void setTuiskipped(boolean tuiskipped) {
		this.tuiskipped = tuiskipped;
	}
	
	public boolean isUrgent() {
		return (urgentStatus == UrgentState.URGENT);
	}


	public void setUrgentStatus(int urgentStatus) {
		this.urgentStatus = urgentStatus;
	}


	public boolean isPrivateStatus() {
		return privateStatus;
	}


	public void setPrivateStatus(boolean privateStatus) {
		this.privateStatus = privateStatus;
	}
	
	public boolean isDeliveryStatus() {
		return isDeliveryStatus;
	}

	public void setDeliveryStatus(boolean isDeliveryStatus) {
		this.isDeliveryStatus = isDeliveryStatus;
	}
	protected Message(Parcel in) {
		uid = in.readLong();
		senderDisplayName = in.readString();
		dateMillis = in.readLong();
		readStatus = in.readByte() != 0;
		urgentStatus = in.readInt();
		isDeliveryStatus = in.readByte() != 0;
		privateStatus = in.readByte() != 0;
		tuiskipped = in.readByte() != 0;
		savedState = in.readInt();
		senderBitmap = in.readParcelable(Bitmap.class.getClassLoader());
		transcription = in.readString();
		contactLookupKey = in.readString();
		contactImageUri = in.readParcelable(Uri.class.getClassLoader());
		fileName = in.readString();
		senderPhoneNumber = in.readString();
		previousUid = in.readLong();
		rowId = in.readLong();
		phoneNumberLabel = in.readString();
		phoneNumberType = in.readInt();
	}

	public static final Creator<Message> CREATOR = new Creator<Message>() {
		@Override
		public Message createFromParcel(Parcel in) {
			return new Message(in);
		}

		@Override
		public Message[] newArray(int size) {
			return new Message[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(uid);
		dest.writeString(senderDisplayName);
		dest.writeLong(dateMillis);
		dest.writeByte((byte) (readStatus ? 1 : 0));
		dest.writeInt(urgentStatus);
		dest.writeByte((byte) (isDeliveryStatus ? 1 : 0));
		dest.writeByte((byte) (privateStatus ? 1 : 0));
		dest.writeByte((byte) (tuiskipped ? 1 : 0));
		dest.writeInt(savedState);
		dest.writeParcelable(senderBitmap, flags);
		dest.writeString(transcription);
		dest.writeString(contactLookupKey);
		dest.writeParcelable(contactImageUri, flags);
		dest.writeString(fileName);
		dest.writeString(senderPhoneNumber);
		dest.writeLong(previousUid);
		dest.writeLong(rowId);
		dest.writeString(phoneNumberLabel);
		dest.writeInt(phoneNumberType);
	}

}

