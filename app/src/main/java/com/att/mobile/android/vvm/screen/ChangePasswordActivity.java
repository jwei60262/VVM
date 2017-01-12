package com.att.mobile.android.vvm.screen;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.util.ArrayList;


public class ChangePasswordActivity extends BasePasswordActivity {


	private EditText confirmPasswordText;
	private ImageView confirmPasswordImage;


	private static final String TAG = ChangePasswordActivity.class.getSimpleName();


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initUI(R.layout.change_password);
		Logger.d(TAG, "ChangePasswordActivity::onCreate");

	}

	protected void setListeners() {
		enterPasswordText.addTextChangedListener(enterPasswordTextWatcher);
		//enterPasswordText.setTransformationMethod(new PasswordTransformationMethod());
		enterPasswordText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {

				if (!hasFocus) {
					if (!isPasswordValid(enterPasswordText, minDigit)) {
						setError(String.format(getString(R.string.password_length), minDigit), enterPasswordImage);
					} else {
						setBackground(enterPasswordImage, getResources().getDrawable(R.drawable.password));
						clearError(false);
						if (isConfirmPasswordValid(confirmPasswordText, enterPasswordText)) {
							clearError(true);
							setBackground(confirmPasswordImage, getResources().getDrawable(R.drawable.password));
						}else{
							btnContinue.setEnabled(false);
						}
					}
				}

			}
		});
	}

	protected void initConfirmPassword() {
		confirmPasswordText = (EditText) findViewById(R.id.confirmPasswordEditText);
		confirmPasswordImage = (ImageView) findViewById(R.id.confirmPasswordImage);
		confirmPasswordText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDigit)});
		confirmPasswordText.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		confirmPasswordText.addTextChangedListener(confirmPasswordTextWatcher);
	}


	protected void changePassword(String pass) {
		if (ModelManager.getInstance().getPasswordChangeRequiredStatus() == PasswordChangeRequiredStatus.RESET_BY_ADMIN) {
            OperationsQueue.getInstance().enqueueXChangeTUIPasswordOperation(pass);
        } else {
            OperationsQueue.getInstance().enqueueSetPasswordMetaDataOperation(pass);
        }
	}



	final TextWatcher confirmPasswordTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {


			int enterPasswordLength = enterPasswordText.getText().length();
			int confirmPasswordLength = confirmPasswordText.getText().length();
			if(enterPasswordLength < minDigit){
				setError(getString(R.string.password_must_be), enterPasswordImage);
				return;
			}
			if(enterPasswordLength > confirmPasswordLength){
				if(!(enterPasswordText.getText()).toString().startsWith(confirmPasswordText.getText().toString())){
					setError(getString(R.string.passwords_not_match), confirmPasswordImage);
				}else{
					setBackground(confirmPasswordImage, getResources().getDrawable(R.drawable.password));
					clearError(false);
				}
			} else if(enterPasswordLength < confirmPasswordLength){
				btnContinue.setEnabled(false);
				setError(getString(R.string.passwords_not_match), confirmPasswordImage);

			}else if((isConfirmPasswordValid(confirmPasswordText, enterPasswordText))){
				setBackground(confirmPasswordImage, getResources().getDrawable(R.drawable.password));
				clearError(isPasswordValid(enterPasswordText, minDigit) && confirmPasswordText.getText().toString().length() >= minDigit);
			}else{
				btnContinue.setEnabled(false);
				setError(getString(R.string.passwords_not_match), confirmPasswordImage);
			}
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void afterTextChanged(Editable s) {

		}


	};

	final TextWatcher enterPasswordTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			if (isPasswordValid(enterPasswordText, minDigit)) {
				setBackground(enterPasswordImage, getResources().getDrawable(R.drawable.password));
				clearError(false);
				if (isConfirmPasswordValid(confirmPasswordText, enterPasswordText)) {
					clearError(true);
					setBackground(confirmPasswordImage, getResources().getDrawable(R.drawable.password));
				} else {
					btnContinue.setEnabled(false);
					if (!TextUtils.isEmpty(confirmPasswordText.getText())) {
						setError(getString(R.string.passwords_not_match), confirmPasswordImage);
					}
				}
			} else {
				btnContinue.setEnabled(false);
			}

		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void afterTextChanged(Editable s) {


		}
	};


	@Override
	protected void onPause() {
		Logger.d(TAG, "ChangePasswordActivity::onPause");
		setSoftKeyboardVisibility(false);
		super.onPause();
	}


	@Override
	public void onBackPressed() {
		if(!isFinishing() && !isFromSettings){
			AlertDlgUtils.showDialog(ChangePasswordActivity.this, R.string.error, R.string.password_not_changed_force, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
				@Override
				public void handlePositiveButton(View view) {
				}
				@Override
				public void handleNegativeButton(View view) {
				}
			});
		}else{
			finish();
		}
	}




	@Override
	public void onUpdateListener(final int eventId, ArrayList<Long> messageIDs) {

		runOnUiThread(new Runnable() {

			@Override
			public void run() {

				switch (eventId) {
					case EVENTS.SET_METADATA_PASSWORD_FINISHED:
					case EVENTS.XCHANGE_TUI_PASSWORD_FINISHED_SUCCESSFULLY:
						// set succeeded result
						setResult(EVENTS.PASSWORD_CHANGE_FINISHED);
						dismissGauge();
						finish();
						break;

					case EVENTS.SET_METADATA_FAILED:
					case EVENTS.LOGIN_FAILED:
						// set failed result
						setResult(EVENTS.PASSWORD_CHANGE_FAILED);
						dismissGauge();
						finish();
						break;

					// when getting minimum and max length of password.
					case EVENTS.GET_METADATA_PASSWORD_FINISHED:
						// stop gauge
						dismissGauge();
						setSoftKeyboardVisibility(true);
						// user canceled, finish

						break;

					case EVENTS.LOGIN_FAILED_DUE_TO_WRONG_PASSWORD:
						dismissGauge();
						setResult(EVENTS.LOGIN_FAILED_DUE_TO_WRONG_PASSWORD);
						finish();
						break;


					case EVENTS.XCHANGE_TUI_PASSWORD_FAILED:
						dismissGauge();
						AlertDlgUtils.showDialog(ChangePasswordActivity.this, R.string.error, R.string.looks_like_something, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
							@Override
							public void handlePositiveButton(View view) {

							}

							@Override
							public void handleNegativeButton(View view) {

							}
						});
						break;

					default:
						break;
				}
			}
		});
		super.onUpdateListener(eventId, messageIDs);
	}



}