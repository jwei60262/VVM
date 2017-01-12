package com.att.mobile.android.vvm.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import com.att.mobile.android.infra.utils.Logger;

public class VVMWidget extends AppWidgetProvider {
	private static final String TAG = "VVMWidget";

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		Logger.d(TAG, "VVMWidget::onUpdate");
		context.startService(new Intent(context,VVMWidgetUpdateService.class));
	}
	

}
