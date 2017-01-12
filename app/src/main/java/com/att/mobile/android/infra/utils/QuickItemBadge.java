package com.att.mobile.android.infra.utils;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
/**
 * 
 *
 * Widget used to show an image with the context sensitive menu 
 * and on-click behavior.  (like in standard android when pressing on menu ) 
 *
 * @author Amit Krelman
 *
 */
public class QuickItemBadge extends ImageView implements OnClickListener {

	
	public QuickItemBadge(Context context) {
		this(context, null);

	}
	public QuickItemBadge(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public QuickItemBadge(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setOnClickListener(this);
	}
	@Override
	public void onClick(View v) {

		
	}

	

}
