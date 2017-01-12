package com.att.mobile.android.vvm.model.greeting;

import java.util.UUID;

import android.os.Parcel;
import android.os.Parcelable;

import com.att.mobile.android.vvm.model.greeting.GreetingFactory.SUPPORTED_GREETING_TYPES;

public class Greeting implements Parcelable {

	private Boolean isChangeable;
	private Boolean isSelected;
	private int maxAllowedRecordTime;
	
	protected SUPPORTED_GREETING_TYPES originalType = null;
	protected String displayName = null;
	protected String description = null;
	protected String uniqueId;
	protected String imapSelectionVariable;
	protected String imapRecordingVariable;

	//TODO should it be a file?
	private boolean hasExistingRecording = false;

	
	public Greeting(Boolean isChangeable, Boolean isSelected, int maxRecordTime) {
		this.isChangeable = isChangeable;
		this.isSelected = isSelected;
		
		// set max allowed record time only for changeable greetings, else it is set to 0
		this.maxAllowedRecordTime = this.isChangeable ? maxRecordTime : 0;
		this.uniqueId = UUID.randomUUID().toString();
	}
	
	public Greeting(Parcel in) {
		readFromParcel(in);
	}
	
	public Boolean isChangeable() {
		return this.isChangeable;
	}

	public Boolean getIsSelected() {
		return this.isSelected;
	}
	
	public void setIsSelected(Boolean isSelected) {
		this.isSelected = isSelected;
	}
	
	public SUPPORTED_GREETING_TYPES getOriginalType() {
		return this.originalType;
	}
	
	public String getDisplayName() {
		return this.displayName;
	}

	public String getUniqueId() {
		return this.uniqueId;
	}
	
	public String getImapSelectionVariable(){
		return this.imapSelectionVariable;
	}
	
	public String getImapRecordingVariable(){
		return this.imapRecordingVariable;
	}
	
	public String getDesc() {
		return this.description;
	}
	
	public int getMaxAllowedRecordTime() {
		return this.maxAllowedRecordTime;
	}

	public void setExistingRecording(boolean hasExistingRecording){
		this.hasExistingRecording = hasExistingRecording;
	}
	
	public boolean hasExistingRecording(){
		return this.hasExistingRecording;
	}
	
	public void setMaxAllowedRecordTime(int maxAllowedRecordTime) {
		this.maxAllowedRecordTime = maxAllowedRecordTime;
	}
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeBooleanArray(new boolean[]{this.isChangeable, this.isSelected});
		dest.writeString(this.originalType.toString());
		dest.writeString(this.displayName);
		dest.writeString(this.description);
		dest.writeInt(this.maxAllowedRecordTime);
		dest.writeString(this.uniqueId);
		dest.writeString(this.imapSelectionVariable);
		dest.writeString(this.imapRecordingVariable);
		dest.writeInt(this.hasExistingRecording? 1:0);
	}
	
	private void readFromParcel(Parcel in) {
		boolean[] boolsArr = new boolean[2];
		in.readBooleanArray(boolsArr);
		
		this.isChangeable = boolsArr[0];
		this.isSelected = boolsArr[1];
		this.originalType = SUPPORTED_GREETING_TYPES.valueOf(in.readString());
		this.displayName = in.readString();
		this.description = in.readString();
		this.maxAllowedRecordTime = in.readInt();
		this.uniqueId = in.readString();
		this.imapSelectionVariable = in.readString();
		this.imapRecordingVariable = in.readString();
		this.hasExistingRecording = (in.readInt() == 1);
	}
	
	public static final Creator<Greeting> CREATOR =
    	new Creator<Greeting>() {
            public Greeting createFromParcel(Parcel in) {
                return new Greeting(in);
            }
 
            public Greeting[] newArray(int size) {
                return new Greeting[size];
            }
        };
}
