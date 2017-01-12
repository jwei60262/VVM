package com.att.mobile.android.vvm.model.greeting;

import android.content.Context;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants.METADATA_VARIABLES;
import com.att.mobile.android.vvm.model.greeting.GreetingFactory.SUPPORTED_GREETING_TYPES;

public class StandartWithNameGreeting extends Greeting{

	public StandartWithNameGreeting(Context context, Boolean isSelected, int maxRecordTime){
		// always changeable
		super(true, isSelected, maxRecordTime);
		
		this.originalType = SUPPORTED_GREETING_TYPES.StandardWithName;
		this.displayName = GreetingFactory.convertOriginalTypeToSimpleType(this.originalType);
		this.description = context.getString(R.string.Greeting_Name_Desc);
		this.imapSelectionVariable = METADATA_VARIABLES.GreetingsStandartWithName;
		this.imapRecordingVariable = METADATA_VARIABLES.RecordedName;
	}
}
