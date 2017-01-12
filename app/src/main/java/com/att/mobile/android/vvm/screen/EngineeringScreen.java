package com.att.mobile.android.vvm.screen;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a screen with some options for use by developers.
 */
public class EngineeringScreen extends VVMActivity {
	private final static String TAG = "EngineeringScreen";

	private SharedPreferences prefs;
	private SharedPreferences.Editor prefsEditor = null;

	private EditText timerDefault;
	private Button mCopyDB;
	// Spinners for testing Watson errors
	protected Spinner mTokenSpinner;
	protected Spinner mTranslSpinner;
	private CheckBox simulateTokenErrorCheckBox;
	private CheckBox retryTokenErrorCheckBox;
	private CheckBox simulateTranslErrorCheckBox;
	private CheckBox retryTranslErrorCheckBox;
	protected Spinner mMinConfidenceSpinner;
	private List<String> list;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.engineering_screen);

		prefs = this.getApplicationContext().getSharedPreferences(
				Constants.KEYS.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE);

		timerDefault = (EditText) findViewById(R.id.defaulttimer);
		int timeout = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PREFERENCE_TIMER_DEFAULT, Integer.class, 30);
		timerDefault.setText(""+timeout);
		mCopyDB = (Button) findViewById(R.id.engScreen_copyDBButton);
		mCopyDB.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mCopyDB.setEnabled(false);
				copyApplicationDBToSDCard();
			}

		});
		mMinConfidenceSpinner = (Spinner) findViewById(R.id.defaultConfidence);
		// Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
		        R.array.confidence_levels, android.R.layout.simple_spinner_item);
		// Specify the layout to use when the list of choices appears
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner
		mMinConfidenceSpinner.setAdapter(adapter);	
		mMinConfidenceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				String value = parent.getItemAtPosition(pos).toString();
				setSharedPreference(Constants.KEYS.MinConfidence, value.trim());
				mMinConfidenceSpinner.setSelection(pos);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		int spinnerPosition = adapter.getPosition(getSharedPreferenceValue(Constants.KEYS.MinConfidence, String.class, Constants.DEFAULT_MIN_CONFIDENCE_LEVEL));
		mMinConfidenceSpinner.setSelection(spinnerPosition);
		boolean simulateTokenError = getSharedPreferenceValue(Constants.KEYS.SIMULATE_TOKEN_ERROR, Boolean.class, false);
		simulateTokenErrorCheckBox = (CheckBox) findViewById(R.id.simulateTokenErrorCheckBox);
		simulateTokenErrorCheckBox.setChecked(simulateTokenError);
//		simulateTokenErrorCheckBox.setTypeface(FontUtils.CLEARVIEW_BOOK);
		simulateTokenErrorCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setSharedPreference(Constants.KEYS.SIMULATE_TOKEN_ERROR, isChecked);
			}
		});

		boolean retryTokenError = getSharedPreferenceValue(Constants.KEYS.TokenRetryFail, Boolean.class, false);
		retryTokenErrorCheckBox = (CheckBox) findViewById(R.id.tokenRetryFailcheckBox);
		retryTokenErrorCheckBox.setChecked(retryTokenError);
//		retryTokenErrorCheckBox.setTypeface(FontUtils.CLEARVIEW_BOOK);
		retryTokenErrorCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setSharedPreference(Constants.KEYS.TokenRetryFail, isChecked);
			}
		});
		boolean simulateTranslError = getSharedPreferenceValue(Constants.KEYS.SIMULATE_TRANSL_ERROR, Boolean.class, false);
		simulateTranslErrorCheckBox = (CheckBox) findViewById(R.id.simulateTranslErrCheckBox);
		simulateTranslErrorCheckBox.setChecked(simulateTranslError);
//		simulateTranslErrorCheckBox.setTypeface(FontUtils.CLEARVIEW_BOOK);
		simulateTranslErrorCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setSharedPreference(Constants.KEYS.SIMULATE_TRANSL_ERROR, isChecked);
			}
		});
		boolean retryTranslError = getSharedPreferenceValue(Constants.KEYS.TranslRetryFail, Boolean.class, false);
		retryTranslErrorCheckBox = (CheckBox) findViewById(R.id.translRetryFailcheckBox);
		retryTranslErrorCheckBox.setChecked(retryTranslError);
//		retryTranslErrorCheckBox.setTypeface(FontUtils.CLEARVIEW_BOOK);
		retryTranslErrorCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setSharedPreference(Constants.KEYS.TranslRetryFail, isChecked);
			}
		});
		initSpinnersList();
	}
	/**
	 * Copies application's database file to the root directory of the SD card.
	 */
	protected void copyApplicationDBToSDCard() {

		new ExportDatabaseFileTask().execute();
	}

	protected void initSpinnersList() {

//		ModelManager.getInstance().saveInSettings(MetaSwitchSettings.TEST_RETRY_SENDING_TIMES,
//				MetaSwitchSettings.TEST_RETRY_OFF, true);
		mTokenSpinner = (Spinner) findViewById(R.id.tokenErrorSpinner);
		list = new ArrayList<String>();
		list.add("400");
		list.add("401");
		list.add("500");
		list.add("503");
		list.add("Unknown");

		ArrayAdapter<String> tokendataAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, list);
		tokendataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTokenSpinner.setAdapter(tokendataAdapter);
		int selection1 = getSelectedError(Constants.KEYS.TokenErrorCode);
		mTokenSpinner.setSelection(selection1);

		mTokenSpinner.setPromptId(R.string.errorcode);
		mTokenSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				String value = parent.getItemAtPosition(pos).toString();
				setSharedPreference(Constants.KEYS.TokenErrorCode, value);
				mTokenSpinner.setSelection(pos);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		
		ArrayAdapter<String> transldataAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, list);
		transldataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTranslSpinner = (Spinner) findViewById(R.id.translErrorSpinner);
		mTranslSpinner.setAdapter(transldataAdapter);
		int selection2 = getSelectedError(Constants.KEYS.TranslErrorCode);
		mTranslSpinner.setSelection(selection2);
		mTranslSpinner.setPromptId(R.string.errorcode);
		mTranslSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				String value = parent.getItemAtPosition(pos).toString();
				setSharedPreference(Constants.KEYS.TranslErrorCode, value);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	}
	private int getSelectedError(String key){
		String code = getSharedPreferenceValue(key, String.class,"400");
		int matchingIndex = -1;
		for(int i=0; i < list.size(); i++){
			if(list.get(i) != null && list.get(i).equals(code)){
				matchingIndex = i;
				break;
			}
		}
		return matchingIndex == -1 ? list.size()-1 : matchingIndex;
	}
	@Override
	public void onBackPressed() {
		if(!TextUtils.isEmpty(timerDefault.getText())){
		ModelManager.getInstance().setSharedPreference(
				Constants.KEYS.PREFERENCE_TIMER_DEFAULT, Integer.parseInt(timerDefault.getText().toString().trim()));
		}
		super.onBackPressed();
	}
	/**
	 * Copies application's database file to the root directory of the SD card.
	 */
	private class ExportDatabaseFileTask extends AsyncTask<Void, Void, Boolean> {
		private final ProgressDialog dialog = new ProgressDialog(
				EngineeringScreen.this);

		// can use UI thread here
		protected void onPreExecute() {
			this.dialog.setMessage("Please Wait...");
			dialog.setCancelable(false);
			this.dialog.show();

		}

		// automatically done on worker thread (separate from UI thread)
		protected Boolean doInBackground(Void... params) {

			File deviceDBFile = getDatabasePath("messages");
			File sdCardDBFile = null;
			FileInputStream deviceDBFileInputStream = null;
			FileOutputStream sdCardDBFileOutputStream = null;
			boolean success = false;

			try {
				// opens application's database file and the file to copy the
				// database to in the SD card

				File directory = Environment.getExternalStorageDirectory();
				sdCardDBFile = new File(directory == null ? new File(
						"/mnt/sdcard") : directory, "/messagesCopy.db");

				// in case the file in the SD card already exist, deletes it
				if (sdCardDBFile.exists()) {
				boolean wasDeleted =	sdCardDBFile.delete();
				Logger.d(TAG, "copyApplicationDBToSDCard - sdCardDBFile.delete() returned " + wasDeleted);
				}
				//
				// creates the SD card file
				boolean wasCreated = sdCardDBFile.createNewFile();
				Logger.d(TAG, "copyApplicationDBToSDCard - sdCardDBFile.createNewFile() returned " + wasCreated);
				// opens the streams of both files
				deviceDBFileInputStream = new FileInputStream(deviceDBFile);
				sdCardDBFileOutputStream = new FileOutputStream(sdCardDBFile);

				// copies the database file to the SD card
				int data;
				while ((data = deviceDBFileInputStream.read()) != -1) {
					sdCardDBFileOutputStream.write(data);
					success = true;
				}
			} catch (Exception ex) {
				Log.e(TAG, "copyApplicationDBToSDCard() - Database file copy to SD card failed.", ex);
			} finally {
				try {
					// release resources if needed
					if (deviceDBFileInputStream != null) {
						deviceDBFileInputStream.close();
					}
					if (sdCardDBFileOutputStream != null) {
						sdCardDBFileOutputStream.close();
					}
				} catch (Exception e) {
					Log.e(TAG, "copyApplicationDBToSDCard() - InputOutputStream close failed.", e);
				}

			}
			return success;
		}

		// can use UI thread here
		protected void onPostExecute(final Boolean success) {
			if (this.dialog.isShowing()) {
				this.dialog.dismiss();
			}
			if (success) {
				Utils.showToast("Database was copied to SD card root directory",Toast.LENGTH_SHORT);

			} else {
				Utils.showToast("Database wasn't copied to SD card root directory", Toast.LENGTH_SHORT);
			}
			mCopyDB.setEnabled(true);
		}

	}

	private synchronized <T extends Object> void setSharedPreference(String key,
			T value) {
		prefsEditor = prefs.edit();

		if (value instanceof Boolean) {
			prefsEditor.putBoolean(key, (Boolean) value);
		} else if (value instanceof Integer) {
			prefsEditor.putInt(key, (Integer) value);
		} else if (value instanceof Float) {
			prefsEditor.putFloat(key, (Float) value);
		} else if (value instanceof Long) {
			prefsEditor.putLong(key, (Long) value);
		} else if (value instanceof String) {
			prefsEditor.putString(key, (String) value);
		} else {
			Logger.d(
					TAG,
					"EngineeringScreen.setSharedPreference() - [key: "
							+ key
							+ "] [value: "
							+ value
							+ " of type "
							+ value
							+ "]. T can be the one of the following types: Boolean, Integer, Float, Long, String.");
		}

		// commit changes
		prefsEditor.commit();

		// release the editor
		prefsEditor = null;
	}
	private  synchronized <T> T getSharedPreferenceValue(String key,
			Class<T> type, T defaultValue) {
		try {
			Object val = prefs.getAll().get(key);
			T res = type.cast(val);

			return res == null ? defaultValue : res;
		} catch (Exception cce) {
			Log.e(TAG,
					"EngineeringScreen.getSharedPreferenceValue() exception", cce);
			return defaultValue;
		}
	}
}
