package com.att.mobile.android.vvm.model.db;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Message;


/**
 * Created by hginsburg on 3/15/2016.
 */
public class VmContentProvider extends ContentProvider {

    private static final String TAG = "VmContentProvider";

    private static final String authorities = "com.att.mobile.android.vvm";
    private static final String GROUPED     = "grouped";

    private static final String contentProviderURL          = "content://" + authorities;
    private static final String contentGroupedProviderURL   = "content://" + authorities + "/" + GROUPED;

    public static final Uri CONTENT_URI             = Uri.parse(contentProviderURL);
    public static final Uri CONTENT_GROUPED_URI     = Uri.parse(contentGroupedProviderURL);

    // Holds query types
    private static final int SINGLE_VM  = 1;
    private static final int GROUPED_BY_CONTACT = 2;

    private static final UriMatcher uriMatcher;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(authorities, "", SINGLE_VM);
        uriMatcher.addURI(authorities, GROUPED, GROUPED_BY_CONTACT);
    }

    @Override
    public boolean onCreate() {

        ModelManager.getInstance();
        return true;
    }

    private static final String GROUP_BY_CONTACT_SQL = new StringBuilder().append("SELECT ").append(ModelManager.Inbox._ID).append(", ").append(ModelManager.Inbox.KEY_UID).append(", ").
            append(ModelManager.Inbox.KEY_PHONE_NUMBER).append(", (select count(i2.").append(ModelManager.Inbox.KEY_UID).append(") from ").
            append(ModelManager.DATABASE_TABLE_INBOX).append(" i2 where ").append(ModelManager.Inbox.KEY_IS_READ).append("=").append(Message.ReadDeletedState.READ).append(" and i2.").append(ModelManager.Inbox.KEY_PHONE_NUMBER).append("=i.").
            append(ModelManager.Inbox.KEY_PHONE_NUMBER).append(" and i2.").toString();
            // Add here query selection
    private static final String GROUP_BY_CONTACT_SQL2 = new StringBuilder().append(" group by i2.").append(ModelManager.Inbox.KEY_PHONE_NUMBER).append(" ) as count_read,").
            append(" (select count(i3.").append(ModelManager.Inbox.KEY_UID).append(") from ").
            append(ModelManager.DATABASE_TABLE_INBOX).append(" i3 where ").append(ModelManager.Inbox.KEY_IS_READ).append("=").append(Message.ReadDeletedState.UNREAD).append(" and i3.").append(ModelManager.Inbox.KEY_PHONE_NUMBER).append("=i.").
            append(ModelManager.Inbox.KEY_PHONE_NUMBER).append(" and i3.").toString();
            // Add here query selection
    private static final String GROUP_BY_CONTACT_SQL3 = new StringBuilder().append(" group by i3.").append(ModelManager.Inbox.KEY_PHONE_NUMBER).append(" ) as count_unread ").
            append("from ").append(ModelManager.DATABASE_TABLE_INBOX).append(" i ").toString();
    private static final String GROUP_BY_CONTACT_SQL4 = new StringBuilder().append(" group by i.").append(ModelManager.Inbox.KEY_PHONE_NUMBER).
            append(" order by i.").append(ModelManager.Inbox.KEY_TIME_STAMP).append(" desc").toString();

    // SELECT SQL for group by contact
    //select _id, phone_number,
    //( select count(i2.uid) from inbox i2 where read_state=0 and i2.phone_number=i.phone_number and i2.saved_state = 4 group by i2.phone_number ) as count_read,
    //( select count(i3.uid) from inbox i3 where read_state=1 and i3.phone_number=i.phone_number and i3.saved_state = 4  group by i3.phone_number ) as count_unread
    //from inbox i
    //group by i.phone_number
    //order by i.time desc


    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        Logger.i(TAG, "query uri=" + uri.toString() + " selection=" + selection);
        Cursor queryResults = null;

        if ( uriMatcher.match(uri) == GROUPED_BY_CONTACT ) {
            String sql = new StringBuilder().append(GROUP_BY_CONTACT_SQL).append(selection)
                    .append(GROUP_BY_CONTACT_SQL2).append(selection)
                    .append(GROUP_BY_CONTACT_SQL3)
                    .append(" where ").append(selection).append(GROUP_BY_CONTACT_SQL4).toString();
            Logger.i(TAG, "query sql=" + sql);
            queryResults = ModelManager.getInstance().getDb().rawQuery(sql, getSelectionFullArgs(selectionArgs));

        } else {
            queryResults = ModelManager.getInstance().getDb().query(ModelManager.DATABASE_TABLE_INBOX, projection, selection, selectionArgs, null, null, sortOrder);
        }

        // registers the queried URI, so that the cursor will be updated ( re-queried ) each time the URI is being changed
        queryResults.setNotificationUri(getContext().getContentResolver(), uri);

        return queryResults;
    }

    /** Create Selection arguments array for grouped sql selection
     *
     * @param selectionArgs
     * @return
     */
    private String[] getSelectionFullArgs (String[] selectionArgs) {
        String[] args = new String[selectionArgs.length*3];
        System.arraycopy(selectionArgs, 0, args, 0, selectionArgs.length);
        System.arraycopy(selectionArgs, 0, args, selectionArgs.length, selectionArgs.length);
        System.arraycopy(selectionArgs, 0, args, selectionArgs.length*2, selectionArgs.length);
        return args;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {

        switch ( uriMatcher.match(uri) ) {
            case SINGLE_VM:
                return "vnd.android.cursor.dir/com.att.mobile.android.vvm";
            case GROUPED_BY_CONTACT:
                return "vnd.android.cursor.item/com.att.mobile.android.vvm/grouped";
            default:
                throw new IllegalArgumentException( "Unsupported URI: " + uri );
        }
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new android.database.SQLException("Read-only access is allowed");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new android.database.SQLException("Read-only access is allowed");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new android.database.SQLException("Read-only access is allowed");
    }
}
