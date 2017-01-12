package com.att.mobile.android.vvm.screen;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;

public class NonAdminUserActivity extends VVMActivity {

	private static final String TAG = "NonAdminUserActivity";


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.d(TAG, "NonAdminUserActivity::onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.non_admin_user);

	}

	@Override
	protected void onResume() {
		super.onResume();
		initTitleFontAndSize();

		((TextView) findViewById(R.id.welcome_to)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Light));
		((TextView) findViewById(R.id.non_admin_user_text)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));


		Button exitButton = (Button) findViewById(R.id.exit_button);
		exitButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		exitButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Logger.d(TAG, "NonAdminUserActivity::exitClickListener");
				setResult(Constants.EVENTS.NON_ADMIN_USER);
				finish();
			}
		});
	}

	public void initTitleFontAndSize(){
		TextView title_att = (TextView)  findViewById(R.id.title_att);
		if(title_att != null) {
			title_att.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_RegularItalic));
			if (getResources().getConfiguration().fontScale >= 1.3f) {
				title_att.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.welcome_screen_title_max_size_for_huge_font));
			}
		}

		TextView title_visual =(TextView)  findViewById(R.id.title_visual);
		if(title_visual != null) {
			title_visual.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_LightItalic));
			if (getResources().getConfiguration().fontScale >= 1.3f) {
				title_visual.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.welcome_screen_title_max_size_for_huge_font));
			}
		}
		setContectDescription();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void setContectDescription() {
		(findViewById(R.id.title)).setClickable(false);
		(findViewById(R.id.title)).setFocusable(false);
		(findViewById(R.id.title)).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
	}

}