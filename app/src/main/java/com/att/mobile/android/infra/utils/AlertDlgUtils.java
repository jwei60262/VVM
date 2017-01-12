package com.att.mobile.android.infra.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;


/**
 * Created by pbahar on 10/15/2015.
 */
public class AlertDlgUtils {

    private static final String TAG = AlertDlgInterface.class.getSimpleName() ;
    static TextView header;
    static TextView body;
    static Button positiveButton;
    static Button negativeButton;



    public static void showDialog(Context context, int headerText, int bodyText, int positiveBtn, int negativeBtn, final boolean cancelOnTouchOutside, final AlertDlgInterface btnActions){

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.alert_dialog);

        final AlertDialog dialog = builder.show();

        dialog.setCanceledOnTouchOutside(cancelOnTouchOutside);
        dialog.setCancelable(cancelOnTouchOutside);

        dialog.setOnKeyListener(new Dialog.OnKeyListener() {

            @Override
            public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if(cancelOnTouchOutside) {
                        dialog.dismiss();
                    }
                    return true;
                }
                return false;
            }
        });

        initUIElements(dialog);

        if(headerText == 0 ){
            header.setVisibility(View.GONE);
        } else {
            header.setVisibility(View.VISIBLE);
            header.setText(headerText);
        }
        body.setText(bodyText);

        setTypeface();

        positiveButton.setText(positiveBtn);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnActions != null)
                {
                    btnActions.handlePositiveButton(v);
                }
                dialog.dismiss();
            }
        });

        if(negativeBtn == 0){
            negativeButton.setVisibility(View.GONE);
        }else{
            negativeButton.setVisibility(View.VISIBLE);
            negativeButton.setText(negativeBtn);
            negativeButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
            negativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (btnActions != null)
                    {
                        btnActions.handleNegativeButton(v);
                    }
                    dialog.dismiss();
                }
            });
        }

    }

    private static void setTypeface() {
        header.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
        body.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
        positiveButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
    }


    public static void showDialogWithCB(Context context, int headerText, int bodyText,int checkboxText, int positiveBtn, int negativeBtn, final AlertDlgInterface btnActions){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.alert_dialog);

        final AlertDialog dialog = builder.show();
        initUIElements(dialog);

        CheckBox cb = (CheckBox)dialog.findViewById(R.id.dialog_checkBox);
        if(checkboxText == 0){
            cb.setVisibility(View.GONE);
        }else {
            cb.setVisibility(View.VISIBLE);
            cb.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
            cb.setText(checkboxText);

            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //Save in shared prefs
                ModelManager.getInstance().setSharedPreference(Constants.DO_NOT_SHOW_SAVED_DIALOG_AGAIN, isChecked);
                 }
            });
        }

        if(headerText == 0 ){
            header.setVisibility(View.GONE);
        } else {
            header.setVisibility(View.VISIBLE);
            header.setText(headerText);
        }
        body.setText(bodyText);

        setTypeface();

        positiveButton.setText(positiveBtn);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnActions.handlePositiveButton(v);
                dialog.dismiss();
            }
        });

        if(negativeBtn == 0){
            negativeButton.setVisibility(View.GONE);
        }else{
            negativeButton.setVisibility(View.VISIBLE);
            negativeButton.setText(negativeBtn);
            negativeButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
            negativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btnActions.handleNegativeButton(v);
                    dialog.dismiss();
                }
            });
        }

    }

    public static void showRightAlignedDialog(Context context, int headerText, int bodyText, int positiveBtn, int negativeBtn, final AlertDlgInterface btnActions){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.alert_dialog);

        final AlertDialog dialog = builder.show();
        initRAUIElements(dialog);
        if(headerText == 0 ){
            header.setVisibility(View.GONE);
        } else {
            header.setVisibility(View.VISIBLE);
            header.setText(headerText);
        }
        body.setText(bodyText);

        setTypeface();

        positiveButton.setText(positiveBtn);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnActions.handlePositiveButton(v);
                dialog.dismiss();
            }
        });

        if(negativeBtn == 0){
            negativeButton.setVisibility(View.GONE);
        }else{
            negativeButton.setVisibility(View.VISIBLE);
            negativeButton.setText(negativeBtn);
            negativeButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
            negativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btnActions.handleNegativeButton(v);
                    dialog.dismiss();
                }
            });
        }

    }
    private static void initUIElements(AlertDialog dialog) {
        header = (TextView)dialog.findViewById(R.id.dialogHeader);
        body = (TextView)dialog.findViewById(R.id.dialogBody);
        positiveButton = (Button)dialog.findViewById(R.id.buttonPositive);
                negativeButton = (Button)dialog.findViewById(R.id.buttonNegative);
    }
    private static void initRAUIElements(AlertDialog dialog) {
        header = (TextView)dialog.findViewById(R.id.dialogHeader);
        body = (TextView)dialog.findViewById(R.id.dialogBody);
        positiveButton = (Button)dialog.findViewById(R.id.buttonPositive);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 0);
        positiveButton.setLayoutParams(params);
        negativeButton = (Button)dialog.findViewById(R.id.buttonNegative);
    }

    public interface AlertDlgInterface {
        void handlePositiveButton(View view);
        void handleNegativeButton(View view);
    }
}
