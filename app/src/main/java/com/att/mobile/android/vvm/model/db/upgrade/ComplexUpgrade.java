package com.att.mobile.android.vvm.model.db.upgrade;

import java.util.ArrayList;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

class ComplexUpgrade extends DBUpgrade {
	
	final ArrayList<DBUpgrade> innerUpgradeScripts = new ArrayList<DBUpgrade>();
	
	@Override
	public void upgrade(SQLiteDatabase db, Context context) {
		for (DBUpgrade upgrader : innerUpgradeScripts){
			upgrader.upgrade(db, context);
		}
	}
}
