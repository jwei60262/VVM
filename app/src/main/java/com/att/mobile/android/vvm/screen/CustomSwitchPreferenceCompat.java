package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.v7.widget.SwitchCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.AccessibilityUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.vvm.R;

/**
 * Created by hginsburg on 3/7/2016.
 */
public class CustomSwitchPreferenceCompat extends SwitchPreferenceCompat {

    private Context mContext;
    private TextView summaryView;
    private View switchView;

    public CustomSwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
    }

    public CustomSwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    public CustomSwitchPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public CustomSwitchPreferenceCompat(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {

        super.onBindViewHolder(holder);

        final TextView titleView = (TextView)holder.findViewById(android.R.id.title);
        if (titleView != null) {
            titleView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        }

        TextView summView = (TextView) holder.findViewById(android.R.id.summary);
        if (summView != null) {
            summaryView = summView;
            summaryView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        }

        SwitchCompat sView = (SwitchCompat)holder.findViewById(android.support.v7.preference.R.id.switchWidget);
        if ( sView != null ) {
            switchView = sView;
            switchView.setFocusable(true);
            switchView.setFocusableInTouchMode(true);
        }

        setTextContentDescription();
    }

    private void setTextContentDescription () {
        if ( summaryView != null ) {
            String switchText = mContext.getString( isChecked() ? R.string.switch_on_desc : R.string.switch_off_desc );
            summaryView.setContentDescription(summaryView.getText() + switchText);
        }
    }

    @Override
    protected void onClick() {
        super.onClick();
        if ( AccessibilityUtils.isAccessibilityActivated() ) {
            String accessibilityText = mContext.getString(isChecked() ? R.string.switch_turn_on_desc : R.string.switch_turn_off_desc);
            AccessibilityUtils.sendEvent(accessibilityText, switchView);
            setTextContentDescription();
        }
    }
}
