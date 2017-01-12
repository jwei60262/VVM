package com.att.mobile.android.vvm.model.db.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;

class AsyncComplexUpgrade extends ComplexUpgrade implements Runnable {
	
	private static String TAG = AsyncComplexUpgrade.class.getSimpleName();

	private final Object _lock = new Object();

	private boolean _isTransactionBegun = false;

	private Context _context;

	private SQLiteDatabase _db;

	@Override
	public void upgrade(SQLiteDatabase db, Context context) {
		_db = db;
		_context = context;
		
		new Thread(this).start();

		synchronized (_lock) {
			try {
				if (!_isTransactionBegun) {
					Logger.d(TAG, "thread waiting for DB transaction to begin");
					_lock.wait();
				}
			} catch (InterruptedException e) {
				Log.e(TAG,"",e);
			}
		}
	}

	@Override
	public void run() {
		_db.beginTransaction();
		
		synchronized (_lock) {
			_isTransactionBegun = true;
			Logger.d(TAG, "db transaction begun notifying waiting threads");
			_lock.notifyAll();
		}
		
		boolean success = false;

		try {
			super.upgrade(_db, _context);
			_db.setTransactionSuccessful();
			success = true;
		} catch (Exception e) {
			Log.e(TAG, "transaction faild on upgrade - rolling db back!");
		} finally {
			_db.endTransaction();
			notifyDBUpgradeEnded(success);
		}
	}

}
