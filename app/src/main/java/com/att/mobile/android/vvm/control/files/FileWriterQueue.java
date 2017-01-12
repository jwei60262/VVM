
package com.att.mobile.android.vvm.control.files;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class FileWriterQueue extends HandlerThread {

	private static final int SAVE_CHUNK = 1;
	private static final int CLOSE_FILE = 2;
	private static final String TAG = "FileWriterQueue";

	private static final int AMR_HEADER_LENGTH = 6;

	/** holds file writer queue single instance */
	private static FileWriterQueue instance = null;

	private FileWriterHandler handler;

	/** holds the number of bytes stored so far for the current handled file */
	private int bytesStored = 0;

	/** holds application's context */
	private Context context = null;

	private static Object lock = new Object();

	/**
	 * Gets the single instance of the file writer queue (creates it if neede)
	 * 
	 * @param context (Context != null) application's context.
	 * @return (FileWriterQueue != null) the single instance of the file writer queue.
	 */
	public static FileWriterQueue getInstance(Context context) {
		// in case the single instance of the file writer queue doesn't exist
		synchronized (lock) {
			if (instance == null) {
				instance = new FileWriterQueue(context);

				// starts file writer queue's thread
				instance.start();
			}
		}
		// returns the single instance of the file writer queue
		return instance;
	}

	/**
	 * FileWriterQueue constructor.
	 */
	private FileWriterQueue(Context context) {
		super(FileWriterQueue.class.getSimpleName());
		this.context = context;
	}

	/**
	 * TODO
	 */
	public synchronized void enqueueFileWriterTask(final FileWriterTask fileWriterTask) {
		if (handler == null) {
			// creates the handler for the file writer queue (using its thread)
			handler = new FileWriterHandler(getLooper());
		}

		handler.sendMessage(handler.obtainMessage(SAVE_CHUNK, fileWriterTask));
	}

	/**
	 * TODO
	 */
	public synchronized void enqueueFileCloserTask(final FileCloserTask fileCloserTask) {
		if (handler == null) {
			// creates the handler for the file writer queue (using its thread)
			handler = new FileWriterHandler(getLooper());
		}
		handler.sendMessage(handler.obtainMessage(CLOSE_FILE, fileCloserTask));
	}

	/**
	 * TODO
	 */
	private class FileWriterHandler extends Handler {
		/**
		 * @param looper
		 */
		public FileWriterHandler(Looper looper) {
			super(looper);
		}

		/**
		 * Handles a message.
		 * 
		 * @param message (Message != null) the message to be handled.
		 */
		@Override
		public void handleMessage(Message message) {
			// in case the message contains file writer task with file's current chunk data
			if (message.what == SAVE_CHUNK) {
				// gets the file writer task
				FileWriterTask fileWriterTask = (FileWriterTask) message.obj;

				// in case the current handled file is first file for its message,
				// and this is its first chunk
				if (fileWriterTask.getMessageFileIndex() == 1 && fileWriterTask.getChunkNumber() == 1) {
					// resets the number of bytes currently stored for the current handled file(s)
					// (for the current handled message)
					bytesStored = 0;
				}

				// stores the current chunk of bytes to the file system, and returns the result (success or failure)
				boolean success = VvmFileUtils.saveChunk(context, fileWriterTask.getFileName(),
						fileWriterTask.getData(), fileWriterTask.getChunkNumber(),
						fileWriterTask.isSkipHeader() ? AMR_HEADER_LENGTH : 0);

				// in case we have managed to save the last chunk successfully - go update the DB
				if (success) {
					// updates the number of bytes stored for the current handled file
					bytesStored += fileWriterTask.getData().length;
					Logger.d(TAG,
							"FileWriterHandler.handleMessage() - chunk #" + fileWriterTask.getChunkNumber()
									+ " saved for file " + fileWriterTask.getFileName()
									+ ", total number of bytes saved till now is " + bytesStored);
				} else {
					bytesStored = 0;
					Logger.d(TAG,
							"FileWriterHandler.handleMessage() - chunk #" + fileWriterTask.getChunkNumber()
									+ " failed to be saved for file " + fileWriterTask.getFileName() + ", file deleted");
				}
			}

			else if (message.what == CLOSE_FILE) {
				// gets the file closer task
				FileCloserTask fileCloserTask = (FileCloserTask) message.obj;

				// in case all message's files bytes have been stored as needed
				if (bytesStored == fileCloserTask.messageFilesSize) {
					Logger.d(TAG,
							"FileWriterHandler.handleMessage() all chunks saved going to update DB with file "
									+ fileCloserTask.fileName);
					ModelManager.getInstance().setMessageFileName(fileCloserTask.messageDo.getId(),
							fileCloserTask.fileName);
				} else {
					Logger.d(TAG,
							"FileWriterHandler.handleMessage() error saving all chunks going to delete file "
									+ fileCloserTask.fileName);
					ModelManager.getInstance().setMessageErrorFile(fileCloserTask.messageDo.getId());
					// in case one or more of the chunks was not saved successfully -
					// delete the file and don't update the DB, file was not saved successfully
					VvmFileUtils.deleteInternalFile(context, fileCloserTask.fileName);
				}
			}
		}
	}
}
