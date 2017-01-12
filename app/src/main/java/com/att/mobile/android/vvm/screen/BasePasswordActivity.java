package com.att.mobile.android.vvm.screen;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.AccessibilityUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 * Created by azelitchenok on 24/02/2016.
 */
public abstract class BasePasswordActivity extends VVMActivity {


    // Minimum digit on password filed
    protected int minDigit;
    protected int maxDigit;

    private static final String TAG = BasePasswordActivity.class.getSimpleName();

    protected boolean isFromSettings = false;

    public static final String FROM_SETTINGS = "from_settings";

    protected TextView errorTV;

    protected Button btnContinue;
    protected EditText enterPasswordText;
    protected ImageView enterPasswordImage;


    abstract void changePassword(String pessword);
    abstract void setListeners();
    abstract void initConfirmPassword();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        isFromSettings = intent == null ? false : intent.getBooleanExtra(FROM_SETTINGS, false);

        minDigit = Integer.parseInt(getString(R.string.minPasswordLenght).trim());
        maxDigit = Integer.parseInt(getString(R.string.maxPasswordLenght).trim());
        OperationsQueue.getInstance().addEventListener(this);
        ModelManager.getInstance().addEventListener(this);
    }


    protected void setError(String error, ImageView iv) {
        errorTV.setTextColor(getResources().getColor(R.color.red));
        errorTV.setText(error);
        btnContinue.setEnabled(false);
        setBackground(iv, getResources().getDrawable(R.drawable.password_error_divider));
        AccessibilityUtils.sendEvent(error, errorTV);
    }

    protected void setBackground(ImageView iv, Drawable drawable) {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            iv. setBackgroundDrawable(drawable);
        }else{
            iv.setBackground(drawable);
        }
    }

    protected void clearError(Boolean setContinueButtonEnable) {
        errorTV.setTextColor(getResources().getColor(R.color.light_grey_dark));
        errorTV.setText(getString(R.string.password_must_be));
        btnContinue.setEnabled(setContinueButtonEnable);

    }
    protected void initErrorTv() {
        errorTV = (TextView) findViewById(R.id.errorTextView);
        errorTV.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
        errorTV.postDelayed(new Runnable() {
            @Override
            public void run() {
                AccessibilityUtils.sendEvent(getString(R.string.password_must_be), errorTV);
            }
        }, 200);
    }


    protected void initEnterPassword() {
        enterPasswordText = (EditText) findViewById(R.id.enterPasswordEditText);
        enterPasswordImage = (ImageView) findViewById(R.id.newPasswordImage);
        enterPasswordText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDigit)});
        enterPasswordText.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
        setListeners();
    }

    protected void initContinueButton() {
        btnContinue = (Button) findViewById(R.id.btnContinue);
        btnContinue.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
        btnContinue.setEnabled(false);
        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!Utils.isNetworkAvailable()) {
                    Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
                } else {
                    clearError(isPasswordValid(enterPasswordText, minDigit));
                    showGauge(getString(R.string.updatingAccountText));
                    String pass = enterPasswordText.getText().toString();
                    changePassword(pass);
                }
            }
        });
    }

    /**
     * Show/Hide the soft keyboard
     */
    protected void setSoftKeyboardVisibility(boolean isVisible) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (enterPasswordText != null) {
                if (isVisible) {
                    imm.showSoftInput(enterPasswordText, InputMethodManager.SHOW_IMPLICIT);
                } else {
                    imm.hideSoftInputFromWindow(this.enterPasswordText.getWindowToken(), 0);
                }
            }
        }
    }



    protected void initUI(int layoutId){
        setContentView(layoutId);
        initErrorTv();
        int titleId = isFromSettings ? R.string.change_password : R.string.password;
        initActionBar(titleId , isFromSettings);

        initEnterPassword();
        initContinueButton();
        initConfirmPassword();
        setSoftKeyboardVisibility(true);
    }


    protected boolean isPasswordValid(EditText et, int minPasswordLenght) {
        String password = et.getText().toString();
        if (password.length() < minPasswordLenght) {
            return false;
        }
        return true;
    }

    protected boolean isConfirmPasswordValid(EditText newPasswordEt, EditText confirmPasswordEt) {
        String conPassword = confirmPasswordEt.getText().toString();
        String newPassword = newPasswordEt.getText().toString();
        if (newPassword.equals(conPassword)) {
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        dismissGauge();
        ModelManager.getInstance().removeEventListener(this);
        OperationsQueue.getInstance().addEventListener(this);
        super.onDestroy();
    }
}
