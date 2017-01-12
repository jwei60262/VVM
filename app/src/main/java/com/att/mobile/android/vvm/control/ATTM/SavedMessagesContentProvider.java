package com.att.mobile.android.vvm.control.ATTM;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Exposes a Content Provider for the VVM Saved Messages .
 * 
 * The content provider authority is: content://com.att.vvm.savedmessages.
 */
public class SavedMessagesContentProvider extends ContentProvider 
{
	public static final String PROVIDER_NAME = "com.att.vvm.savedmessages";
	public static final Uri CONTENT_URI = Uri.parse("content://" + PROVIDER_NAME);

	private static final String MESSAGE_ID = "messageid";
	private static final String FILE_NAME = "filename";
	
    
    private ModelManager modelManager; 
    private final static String TAG = "SavedMessagesContentProvider";
   
   /**
    * retrieve attachment file a specific message either by message id segment or attachment file name segment 
    * segment messageid = com.att.vvm.savedmessages/messageid/2"
    * segment filename = com.att.vvm.savedmessages/filename/6174601743_2.amr"
    *    
    */
    @Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    	
		Log.i("SavedMessagesContentProvider.openFile", "Retrieve file according to message ID or file name");

		String segment = uri.getPathSegments().get(0);
		String id = uri.getPathSegments().get(1);

		String filePath = new StringBuilder(VVMApplication.getContext().getFilesDir().getPath()).append(File.separator)
				.toString();
		String fileName = "";
		if (segment.equalsIgnoreCase(MESSAGE_ID)) {

			long messageId = Long.parseLong(id);
			Log.i("SavedMessagesContentProvider.openFile", "Retrieve file according to message ID " + messageId);

			Cursor cursor = modelManager.getMessage(messageId);
			if (cursor != null && cursor.moveToFirst()) {
				fileName = cursor.getString(cursor.getColumnIndex("file_name"));
				cursor.close();
			}

		} else if (segment.equalsIgnoreCase(FILE_NAME)) {

			fileName = id;
			Log.i("SavedMessagesContentProvider.openFile", "Retrieve file according to file name " + fileName);
		}
		
		String fullFilePath = new StringBuilder(filePath).append(fileName).toString();
		File messageFile = new File(fullFilePath);
		return ParcelFileDescriptor.open(messageFile, ParcelFileDescriptor.MODE_READ_ONLY); 
    	
	}

	/**
     * Initializes the Content Provider.
     * 
     * @return (boolean) true in case the provider was successfully initialized.
    
     */
    @Override
    public boolean onCreate()
    {
    	ModelManager.createInstance(VVMApplication.getContext());
    	modelManager = ModelManager.getInstance();
			 
        return true;
    }

    /**
     * Performs a query against the Messages table for saved messages.        
     * 
     * parameters should be kept null
     * @return cursor (Cursor) a cursor contains query's results. Should be closed after use 
     * 			Columns in Cursor: 	_id (int)
     * 								uid (int)
     * 								time (int) 
     * 								phone_number (String)
     * 								transcription (String)
     * 								file_name (String)
     * 								read_state (int)
     * 								saved_state (int)
     * 								forward_state (int)
     * 								urgent (int)
     * 								delivery_status (int)  							 	
     * 			
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sort)
    {	
    	
    	Logger.i(TAG, "performing a query against the Messages table for saved messages");
    	if (modelManager== null){
    		modelManager = ModelManager.getInstance();
    	}       

        //performs the requests query against the messages table
        Cursor queryResults = modelManager.getAllMessagesFromInbox(/*MessageFilter.TYPE_SAVED*/);

        //registers the queried URI, so that the cursor will be updated (re-queried) each time the URI is being changed
        queryResults.setNotificationUri(getContext().getContentResolver(), uri);
       
        //returns the query results 
        return queryResults; 
        
    }
    
    

    /**
     * Not implemented for this content provider.
     */
    @Override
    public Uri insert(Uri _uri, ContentValues _initialValues)
    {
        throw new SQLException("Read-only access is allowed");
    }

    /**
     * Not implemented for this content provider.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs)
    {
        throw new SQLException("Read-only access is allowed");
    }

    /**
     * Not implemented for this content provider.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs)
    {
        throw new SQLException("Read-only access is allowed");
    }

    /**
     * Not implemented for this content provider.
     */
    @Override
    public String getType(Uri _uri)
    {
       return null; 
    }
}
