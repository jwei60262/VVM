package com.att.mobile.android.vvm.screen;

/**
 * Created by nslesuratin on 11/22/2015.
 */

import android.database.Cursor;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;

import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 *
 *
 */
public class CursorPagerAdapter extends FragmentStatePagerAdapter {
     private Cursor cursor;
    private boolean isAutoPlay;
    /**
     *
     * fm
     * fragmentClass
     * @param cursor
     * @param isAutoPlay
     */
    public CursorPagerAdapter(FragmentManager fm, Cursor cursor, boolean isAutoPlay) {
        super(fm);
         this.cursor = cursor;
        this.isAutoPlay = isAutoPlay;
    }


    @Override
    public PlayerFragment getItem(int position) {
        if (cursor == null)
            return null;

        cursor.moveToPosition(position);
        PlayerFragment frag;
        Message mes = ModelManager.getInstance().createMessageFromCursor(cursor);
        try {
            frag = PlayerFragment.init(mes, position, cursor.getCount(), isAutoPlay );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return frag;
    }
    @Override
    public int getItemPosition(Object object) {
        // refresh all fragments when data set changed
        return PagerAdapter.POSITION_NONE;
    }


    @Override
    public int getCount() {
        if (cursor == null)
            return 0;
        else
            return cursor.getCount();
    }

//    public void swapCursor(Cursor c) {
//        if (cursor == c)
//            return;
//
//        this.cursor = c;
//        notifyDataSetChanged();
//    }
    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
     * closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there wasa not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    private Cursor swapCursor(Cursor newCursor) {
        if (newCursor == cursor) {
            return null;
        }
        Cursor oldCursor = cursor;
        cursor = newCursor;
        if (newCursor != null) {
            // notify the observers about the new cursor
            notifyDataSetChanged();
        }
        return oldCursor;
    }

    public Cursor getCursor() {
        return cursor;
    }
    public Message getCurrentMessage(){
        return ModelManager.getInstance().createMessageFromCursor(cursor);
    }
}