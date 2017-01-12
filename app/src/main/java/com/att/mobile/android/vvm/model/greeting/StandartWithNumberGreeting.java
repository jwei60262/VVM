package com.att.mobile.android.vvm.model.greeting;

import android.content.Context;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants.METADATA_VARIABLES;
import com.att.mobile.android.vvm.model.greeting.GreetingFactory.SUPPORTED_GREETING_TYPES;

public class StandartWithNumberGreeting extends Greeting{

	public StandartWithNumberGreeting(Context context, Boolean isChangeable, Boolean isSelected, int maxRecordTime){
		super(isChangeable, isSelected, maxRecordTime);
		
		this.originalType = SUPPORTED_GREETING_TYPES.StandardWithNumber;
		this.displayName = GreetingFactory.convertOriginalTypeToSimpleType(this.originalType);
		this.description = context.getString(R.string.Greeting_Default_Desc);
		this.imapSelectionVariable = this.isChangeable() ? METADATA_VARIABLES.GreetingsStandartWithNumber : null;
		this.imapRecordingVariable = this.isChangeable() ? METADATA_VARIABLES.GreetingsStandartWithNumber : null;
	}
}
