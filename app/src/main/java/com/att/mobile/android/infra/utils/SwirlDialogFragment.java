package com.att.mobile.android.infra.utils;


import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import com.att.mobile.android.vvm.R;


/**
 * Created by azelitchenok on 22/2/2016.
 */
public class SwirlDialogFragment extends DialogFragment {

    private String mBodyText;
    LoadingProgressBar loadingProgressBar;

    public void setBodyText(String bodyText){
        mBodyText = bodyText;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dialog_with_swirly, container);
        loadingProgressBar = (LoadingProgressBar) view.findViewById(R.id.gaugeSetupProgress);

        ((TextView)view.findViewById(R.id.txtHeader)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
        TextView bodyTextView = (TextView)view.findViewById(R.id.txtSetupLoading);
        if(TextUtils.isEmpty(mBodyText)){
            bodyTextView.setVisibility(View.INVISIBLE);
        } else {
            bodyTextView.setText(mBodyText);
            bodyTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
        }

        loadingProgressBar.start();
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        return view;
    }
}
