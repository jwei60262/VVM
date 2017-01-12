package com.att.mobile.android.vvm.ui_components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.vvm.R;

/**
 * Created by evinouze on 23/03/2016.
 */
public class CompoundTextWithBullet extends LinearLayout{

    private RelativeLayout rowFrame;
    private TextView text;


    public CompoundTextWithBullet(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (isInEditMode()) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.text_with_bullet, this);

        rowFrame = (RelativeLayout) findViewById(R.id.bulleted_text_layout);
        text = (TextView) findViewById(R.id.bulleted_text);
        text.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Regular));

        initializeAttributes(context, attrs);
    }

    private void initializeAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CompoundTextWithBullet, 0, 0);
        try {
            int textSrc = a.getResourceId(R.styleable.CompoundTextWithBullet_textSrc, 0);
            if (textSrc != 0) {
                text.setText(textSrc);
            }
            int textColor = a.getColor(R.styleable.CompoundTextWithBullet_text_color, Color.BLACK);
            text.setTextColor(textColor);

        } finally {
            a.recycle();
        }
    }

    public void setText (int strigId){
        text.setText(strigId);
    }
}
