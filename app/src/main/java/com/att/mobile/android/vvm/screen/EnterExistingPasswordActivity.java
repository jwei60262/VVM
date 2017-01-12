package com.att.mobile.android.vvm.screen;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.util.ArrayList;

public class EnterExistingPasswordActivity extends BasePasswordActivity {


	int passwordChangeRequiredStatus;
	private static final String TAG = EnterExistingPasswordActivity.class.getSimpleName();


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initUI(R.layout.enter_existing_password);
		// check if no more unread messages - clear notification
		((VVMApplication) (getApplicationContext())).clearNotification();

		passwordChangeRequiredStatus = ModelManager.getInstance().getPasswordChangeRequiredStatus();

	}


	@Override
	void setListeners() {
		enterPasswordText.addTextChangedListener(enterPasswordTextWatcher);
	}

	@Override
	void initConfirmPassword() {
		//relevant only for ChangePasswordActivity
	}

	final TextWatcher enterPasswordTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}
		@Override
		public void afterTextChanged(Editable s) {

			if (isPasswordValid(enterPasswordText, minDigit)) {
				setBackground(enterPasswordImage, getResources().getDrawable(R.drawable.password));
				clearError(isPasswordValid(enterPasswordText, minDigit));
			}else{
				setError(String.format(getString(R.string.password_length), minDigit), enterPasswordImage);

			}
		}
	};


	protected void changePassword(String pass) {
		ModelManager.getInstance().setPassword(pass);
		// just put a short operation to check login is succeeded
		OperationsQueue.getInstance().enqueueGetPasswordLengthOperation();
	}

	@Override
	protected void onResume() {
		setSoftKeyboardVisibility(true);
		super.onResume();

	}

	@Override
	protected void onPause() {
		setSoftKeyboardVisibility(false);
		super.onPause();

	}



	/**
	 * if login is failed we want to tell it to the welcome activity and return
	 * to this screen again if login succeeded we want to return to the welcome
	 * activity after updating the no password change is required and let the
	 * welcome activity navigate us back
	 */
	@Override
	public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {

        Logger.i(TAG, "onUpdateListener eventId=" + eventId);
        switch (eventId){

			case EVENTS.LOGIN_FAILED_DUE_TO_WRONG_PASSWORD:
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						dismissGauge();
						//setErrorMessage(getString(R.string.wrong_passwords), true);

						AlertDlgUtils.showDialog(EnterExistingPasswordActivity.this, R.string.error, R.string.wrong_passwords, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
							@Override
							public void handlePositiveButton(View view) {
							}

							@Override
							public void handleNegativeButton(View view) {
							}
						});
					}
				});
				break;

			case EVENTS.LOGIN_FAILED:
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						dismissGauge();
						AlertDlgUtils.showDialog(EnterExistingPasswordActivity.this, R.string.error, R.string.looks_like_something, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
                            @Override
                            public void handlePositiveButton(View view) {
                            }

                            @Override
                            public void handleNegativeButton(View view) {

                            }
                        });
					}
				});

				break;

			case EVENTS.LOGIN_SUCCEEDED:
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						dismissGauge();
						ModelManager.getInstance().setPasswordChangeRequired(PasswordChangeRequiredStatus.NONE);
						// no longer go back to see the welcome screen
//					ModelManager.getInstance().setFirstTimeUse(false);
						setResult(EVENTS.ENTER_PASSWORD_FINISHED);
						finish();
					}
				});
				break;

            case EVENTS.START_WELCOME_ACTIVITY:
                if ( ModelManager.getInstance().getCurrentSetupState() !=  Constants.SETUP_STATUS.ENTER_EXISTING_PWD ) {
                    super.onUpdateListener(eventId, messageIDs);
                }
                break;

			default:
				super.onUpdateListener(eventId, messageIDs);
		}
	}



	@Override
	public void onBackPressed() {
		if(!isFinishing()){
			AlertDlgUtils.showDialog(EnterExistingPasswordActivity.this, R.string.error, R.string.passwordMissmatchScreenWarning, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
				@Override
				public void handlePositiveButton(View view) {
				}

				@Override
				public void handleNegativeButton(View view) {
				}
			});
		}
	}


}
