package com.att.mobile.android.vvm.model.db.upgrade;

import java.util.ArrayList;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 * DB upgrade factory.
 * 
 * @author sbandela
 * 
 */
public final class DBUpgradeFactory {
	
	private static final String TAG = "DBUpgradeFactory";
	
	public static final DBUpgrade getDBUpgradeScript(int oldVersion, int newVersion) {

		Logger.d(TAG, "getDBUpgradeScript() oldVersion = " + oldVersion + " newVersion = " + newVersion);

		// create a composition of upgrade scripts
		ComplexUpgrade complexUpgrader = new AsyncComplexUpgrade();
		ArrayList<DBUpgrade> scripts = complexUpgrader.innerUpgradeScripts;
		

		if (oldVersion < ModelManager.DB_VERSION_2 && newVersion >= ModelManager.DB_VERSION_2) {

			Logger.d(TAG, "getDBUpgradeScript() upgrade from version " + oldVersion + " to version " + newVersion + " start");
			scripts.add(new AddWatsonStatusColumnToInboxTable());
			Logger.d(TAG, "getDBUpgradeScript() upgrade from version " + oldVersion + " to version "+ newVersion + " end");
		}
		return scripts.isEmpty() ? NullObjectHolder.NULL : complexUpgrader;
	}

	/**
	 * Lazy loader to avoid allocating if not in use...
	 */
	static final class NullObjectHolder {
		static final DBUpgrade NULL = new DBUpgrade() {
			@Override
			public void upgrade(SQLiteDatabase db, Context context) {
				// do nothing...
			}
		};
	}
}
