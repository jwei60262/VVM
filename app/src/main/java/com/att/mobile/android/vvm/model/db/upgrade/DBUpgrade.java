package com.att.mobile.android.vvm.model.db.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;

public abstract class DBUpgrade {
	
	private OnUpgradeListener _listener = null;
	
	public abstract void upgrade(SQLiteDatabase db, Context context);
	
	public final void setUpgradeListener(OnUpgradeListener listener) {
		_listener = listener;
	}
	
	final void notifyDBUpgradeEnded(boolean success) {
		if (_listener != null) {
			Message payload = _listener.obtainMessage();
			payload.obj = success;			
			payload.sendToTarget();
		}
	}
	
	public static abstract class OnUpgradeListener extends Handler {
		@Override
		public final void handleMessage(Message msg) {
			onDBUpgradeEnded((Boolean) msg.obj);
		}

		protected abstract void onDBUpgradeEnded(boolean success);
	}
}
