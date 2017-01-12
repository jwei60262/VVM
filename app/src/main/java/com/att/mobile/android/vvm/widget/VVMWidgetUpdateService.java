package com.att.mobile.android.vvm.widget;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class VVMWidgetUpdateService extends IntentService {
	public static final String TAG = "VVMWidgetUpdateService";

	public VVMWidgetUpdateService() {
		super("VVMWidgetUpdateService");
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Context context = VVMApplication.getContext();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
		int messageCount = ModelManager.getInstance().getNewMessagesCount();
		Logger.d(TAG, "onHandleIntent messageCount = "+ messageCount);
		if (messageCount > 0) {
			String strUnReadMessagesNum = Integer.toString(messageCount);				
			views.setViewVisibility(R.id.textView, View.VISIBLE);
			views.setTextViewText(R.id.textView, strUnReadMessagesNum);
			views.setContentDescription(R.id.imageButton, getString(R.string.app_name)+ " "+strUnReadMessagesNum+" "+(messageCount == 1 ? getString(R.string.unreadMessageText) :getString(R.string.unreadMessagesTextEnd)));

		}else{
			views.setViewVisibility(R.id.textView, View.GONE);				
			views.setContentDescription(R.id.imageButton, getString(R.string.app_name));
		}
		Intent confintent = Utils.getLaunchingIntent(context, false);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, confintent, PendingIntent.FLAG_UPDATE_CURRENT);
	
		views.setOnClickPendingIntent(R.id.imageButton, pendingIntent);
        ComponentName thisWidget = new ComponentName(context, VVMWidget.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(thisWidget, views);
	}

}
