package com.att.mobile.android.vvm.model.db.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.db.ModelManager.Inbox;


public class AddWatsonStatusColumnToInboxTable extends DBUpgrade {
	
	private static final String TAG = "AddWatsonStatusColumnToInboxTable";

	@Override
	public void upgrade(SQLiteDatabase db, Context context) {
		db.execSQL("ALTER TABLE " + ModelManager.DATABASE_TABLE_INBOX + " ADD "
				+ Inbox.KEY_WATSON_TRANSCRIPTION + "  INT(1) DEFAULT 0 ");

		Logger.d(TAG, "Watson transcription column was added to threads table after upgrade");

	}
	
}
