package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;

/**
 * Created by hginsburg on 3/7/2016.
 */
public class CustomPreference extends Preference {
    public CustomPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CustomPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView titleView = (TextView)holder.findViewById(android.R.id.title);
        if (titleView != null) {
            titleView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        }

        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        }
    }
}
