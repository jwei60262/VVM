package com.att.mobile.android.vvm.screen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class DebugSettingsScreen extends VVMActivity {
	private String userName;
	private String userPass;
	private String hostName;
	private TextView manualHost;
	private static final String TAG = "DebugSettingsScreen";

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.debug_settings);

		manualHost = (TextView) findViewById(R.id.manualHost);
		
		Button login = (Button) findViewById(R.id.login);
		login.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				saveDebugSettings();
			}
		});

		Spinner spinner = (Spinner) findViewById(R.id.spinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter
				.createFromResource(this, R.array.hosts_array,
						android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new MyOnItemSelectedListener());
		manualHost.setText("");
		manualHost.refreshDrawableState();
	}

	private void saveDebugSettings() {
		TextView settingUserInput = (TextView) findViewById(R.id.settingUserInput);
		TextView settingPasswordInput = (TextView) findViewById(R.id.settingPasswordInput);
		CheckBox tempPassword = (CheckBox) findViewById(R.id.tempPassword);
		CheckBox sslOn = (CheckBox) findViewById(R.id.sslOn);
		CheckBox fakeLogin = (CheckBox) findViewById(R.id.fakeLogin);

		userName = settingUserInput.getText().toString();
		userPass = settingPasswordInput.getText().toString();
		hostName = manualHost.getText().toString();

		if (!TextUtils.isEmpty(userName) && !TextUtils.isEmpty(userPass) && !TextUtils.isEmpty(hostName)) {
			ModelManager.getInstance().setSharedPreference(
					Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, userName);
			ModelManager.getInstance().setPassword(userPass);
			ModelManager.getInstance().setSharedPreference(
					Constants.KEYS.PREFERENCE_HOST, hostName);

			// to simulate an uninitilized newly created aml box - open this
			// remark
			if (tempPassword.isChecked()) {
				ModelManager.getInstance().setPasswordChangeRequired(
						PasswordChangeRequiredStatus.TEMPORARY_PASSWORD);
			}
			
			// save ssl on
			ModelManager.getInstance().setSharedPreference(Constants.KEYS.PREFERENCE_DEBUG_SSL_ON, sslOn.isChecked());
			if(fakeLogin.isChecked()){
				ModelManager.getInstance().setSharedPreference(
						Constants.KEYS.PREFERENCE_TOKEN, "4042634387:A:OJUSAP46:ms18:IMSTTD:11069");
			}
			//4042634387:A:OJUSAP46:ms18:IMSTTD:11069
			//4042633041:A:OJUSAP46:ms17:IMSTTD:50561
			ModelManager.getInstance().setSharedPreference(
						Constants.KEYS.PREFERENCE_PORT, "143");
			ModelManager.getInstance().setSharedPreference(
						Constants.KEYS.PREFERENCE_SSL_PORT, "993");

			setResult(EVENTS.IDENTIFY_USER_FINISHED);
			Intent intent = new Intent(this, WelcomeActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
		}
	}

	@Override
	public void onBackPressed() {
		setResult(EVENTS.IDENTIFY_USER_FAILED);
		super.onBackPressed();
	}

	public class MyOnItemSelectedListener implements OnItemSelectedListener {

			public void onItemSelected(AdapterView<?> parent, View view, int pos,
					long id) {
				
				String host = parent.getItemAtPosition(pos).toString();
				
				if (host.equalsIgnoreCase("none")){
					manualHost.setText("");
				}else{
					manualHost.setText(host);
				}
				manualHost.refreshDrawableState();
				Logger.d(TAG, "Host set to: " + host);

			}

		public void onNothingSelected(AdapterView<?> parent) {
			// Do nothing.
		}
	}
}