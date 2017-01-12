
package com.att.mobile.android.vvm.screen;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.ATTM.AttmUtils;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.KEYS;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class ATTMLauncherActivity extends VVMActivity {
	private ModelManager modelManager;
	private static final String TAG = "ATTMLauncherActivity";

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Logger.d(TAG, "ATTMLauncherActivity()");

		modelManager.setSharedPreference(KEYS.DO_NOT_SHOW_LAUNCH_ATTM_SCREEN, true); // next time no screen will
		// be shown
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.d(TAG, "ATTMLauncherActivity.onDestroy()");
		super.onCreate(savedInstanceState);
		// get model manager instance
		ModelManager.createInstance(getApplicationContext());
		modelManager = ModelManager.getInstance();

		setContentView(R.layout.attm_launcher);

		setFontAndButton();
	}

	private void setFontAndButton() {
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

		((TextView) findViewById(R.id.attm_launcher_title)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		((TextView) findViewById(R.id.attm_launcher_paragraph1)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));

		Button btnLaunchATTM = (Button) findViewById(R.id.open_att_button);
		btnLaunchATTM.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		btnLaunchATTM.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (AttmUtils.launchATTMApplication()) {
					setResult(EVENTS.BACK_FROM_ATTM_LAUNCHAR);
					finish();
				}
			}
		});

	}

	@Override
	public void onBackPressed() {
		if (AttmUtils.launchATTMApplication()) {
			setResult(EVENTS.BACK_FROM_ATTM_LAUNCHAR);
			super.onBackPressed();
		}
	}
}
