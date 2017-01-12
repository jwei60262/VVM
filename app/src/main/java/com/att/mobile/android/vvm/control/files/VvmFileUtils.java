package com.att.mobile.android.vvm.control.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.screen.VVMActivity;

/**
 * Handle files reading, writing, encoding and decoding the help manage the
 * voice attachments in the android file system
 * 
 * @author ldavid
 * 
 */
public class VvmFileUtils {

	public static final int OPEN_FILE = 1;
	public static final int APPEND = 2;
	public static final int APPEND_AND_CLOSE = 3;
	public static final int CLOSE = 4;
	private static final String TAG = "VvmFileUtils";

	/**
	 * holds a media scanner callback for when copied file is being scanned as
	 * media
	 */
	private static class OnVVMScanCompletedListener implements OnScanCompletedListener{
		Context context;
		boolean shareFile = false;
		Uri fileDirUri;
		public OnVVMScanCompletedListener(Context context, boolean shareFile, Uri uri){
			this.context = context;
			this.shareFile = shareFile;
			this.fileDirUri = uri;
		}
		/**
		 * Called when media scanning is completed.
		 */
		@Override
		public void onScanCompleted(String path, Uri uri) {
			Logger.d(TAG,
					"VvmFileUtils.OnVVMScanCompletedListener - media at "
							+ path + " has been scanned, URI is " + uri);
			if(uri == null){
				uri = this.fileDirUri;
			}
			
			if (shareFile && context != null && uri != null){
				Logger.d(TAG, "VvmFileUtils.OnVVMScanCompletedListener going to show share menu");
				
				VVMActivity.showAudioShareMenu(context, uri);
			} else {
				Logger.d(TAG, "VvmFileUtils.OnVVMScanCompletedListener do not show share menu context = "+ context+ " uri = "+ uri);

			}
		}
	}
	
	private VvmFileUtils() {
		throw new AssertionError();
	}

	/**
	 * Check if a file already exists in the file system
	 * 
	 * @param path
	 * @return
	 */
	public static boolean isExists(String path) {

		File file = new File(path);
		return file.exists();
	}

	/**
	 * get the number of files of fileType within a folder
	 * 
	 * @author istelman
	 * @param folder
	 * @param fileType
	 * @return
	 */
	public static int getFileCount(String folder, final String fileType) {
		File dir = new File(folder);

		if (!dir.exists() || !dir.isDirectory()) {
			return 0;
		}

		FilenameFilter filter = new FilenameFilter() {

			@Override
			public boolean accept(File file, String s) {
				return s.endsWith(fileType);
			}
		};

		return dir.listFiles(filter).length;
	}

	/**
	 * Save file to the private application persistent storage
	 * 
	 * @param context
	 * @param fileName
	 * @param data
	 * @param bytesToSkip
	 *            (int) the number of bytes to skip.
	 * @return the full path of the saved file
	 */
	public static boolean saveChunk(Context context, String fileName,
			byte[] data, int chunkNum, int bytesToSkip) {

		FileOutputStream fos = null;

		try {
			if(context != null){
				fos = context.openFileOutput(fileName, Context.MODE_APPEND);
				byte[] decodedData = Base64.decode(data, Base64.DEFAULT);
				if(fos != null){
					fos.write(decodedData, bytesToSkip, decodedData.length	- bytesToSkip);
					fos.flush();
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			Logger.d(TAG, "failed chunk is:\n" + new String(data));
			// delete the file on error
			deleteInternalFile(context, fileName);
			Log.e(TAG,
					"VvmFileUtils.saveChunk() file deleted " + fileName);
			return false;
		} finally {
			try {
				if(fos != null){
					fos.close();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * Copies a file from application's internal storage to device's external
	 * storage (not application's external storage).
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileToCopyName
	 *            (String != null) the name of the file to be copied from
	 *            application's internal storage.
	 * @param copiedFileName
	 *            (String != null) the name of the copied file in device's
	 *            external storage.
	 * @param fileType
	 *            (String != null) the type of the copied file which the file
	 *            will be located according to.
	 * @return (boolean) true in case the file was successfully copied, false
	 *         otherwise.
	 */
	public static boolean copyFileToDeviceExternalStorage(Context context,
			String fileToCopyName, String copiedFileName, String fileType,
			boolean shareFile) {
		try {
			// in case external storage doesn't exist on the device or busy
			if (!Environment.MEDIA_MOUNTED.equals(Environment
					.getExternalStorageState())) {
				return false;
			}

			// gets the path for the copied file, according to its type
			File copiedFileExternalStorageFolder = Environment.getExternalStoragePublicDirectory(fileType);
			// Make sure the directory exists
			boolean wasMade = copiedFileExternalStorageFolder.mkdirs();
			Logger.d(TAG, "copyFileToDeviceExternalStorage() - copiedFileExternalStorageFolder.mkdirs() returned " + wasMade);

			File copiedFile = new File(copiedFileExternalStorageFolder,
					copiedFileName);

			// copies the file to device's external storage
			boolean wasCopied = copyFile(new File(context.getFilesDir(), fileToCopyName), copiedFile, 0);

			// in case the file wasn't copied
			if (!wasCopied) {
				return false;
			}

			// in case the media scan should be done
				
					// create a media scan complete listenenr
//					OnVVMScanCompletedListener scanCompletedListener = new OnVVMScanCompletedListener(context, shareFile, Uri.fromFile(copiedFile));
//						MediaScannerConnection.scanFile(context,
//								new String[] { copiedFile.getPath() }, null,
//								scanCompletedListener);
				

			return true;
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			return false;
		}
	}
	
	
	 
		public static boolean copyFileToMediaStore(Context context,
				String fileToCopyName, String copiedFileName, String fileType,
				boolean shareFile) {
			try {

				Uri fileUri = Uri.withAppendedPath(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, copiedFileName);
				// gets the path for the copied file, according to its type
				File copiedFile = new File(fileUri.getPath());

				// copies the file to device's external storage
				boolean wasCopied = copyFile(new File(context.getFilesDir(), fileToCopyName), copiedFile, 0);

				// in case the file wasn't copied
				if (!wasCopied) {
					return false;
				}
						// create a media scan complete listenenr
						OnVVMScanCompletedListener scanCompletedListener = new OnVVMScanCompletedListener(context, shareFile, null);
							MediaScannerConnection.scanFile(context,
									new String[] { copiedFile.getPath() }, null,
									scanCompletedListener);

				return true;
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				return false;
			}
		}
	
	/**
	 * general method to copy file from a target inputstream into a given file
	 * @param inputStream
	 * @param copiedFile
	 */
	public static void copyFile(InputStream inputStream, File copiedFile) {
		try {
			OutputStream out = new FileOutputStream(copiedFile);
			byte buf[] = new byte[1024];
			int len;
			while ((len = inputStream.read(buf)) > 0) {
				out.write(buf, 0, len);
			}

			out.close();
			inputStream.close();

		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}  catch (Exception ex) {
			Log.e(TAG, ex.getMessage(), ex);
		}
	}

	/**
	 * copy file into the current package as readable so it can be share with
	 * our apps.
	 * 
	 * @param inputStream
	 * @param copiedFileName
	 * @return
	 */
	public static boolean copyFileAsReadable(Context context,
			String sourceFileName, String targetFileName) {

		InputStream sourceFileInputStream = null;
		OutputStream targetFileOutputStream = null;
		try {
			sourceFileInputStream = context.openFileInput(sourceFileName);
			targetFileOutputStream = context.openFileOutput(targetFileName,
					Context.MODE_WORLD_READABLE);
			byte buf[] = new byte[1024];
			int len;
			while ((len = sourceFileInputStream.read(buf)) > 0) {
				targetFileOutputStream.write(buf, 0, len);
			}
			Logger.d(TAG, "copyFileAsReadable() file "
					+ targetFileName + "copied successfully");

		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			return false;
		} finally {
			try {
				if (targetFileOutputStream != null) {
					targetFileOutputStream.close();
				}
				if (sourceFileInputStream != null) {
					sourceFileInputStream.close();
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				return false;
			}
		}

		return true;
	}
	
	/**
	 * Reads files according to path
	 * 
	 * @param filePath 
	 * 			Path of the file to read 
	 * @return the byte array in which to store the bytes read.
	 */
	public static byte[] getFileBytes(String filePath) {

		Log.d(TAG, "VvmFileUtils.fileBuffter() - filePath: " + filePath);
		byte fileContent[] = null;
		FileInputStream fin = null;
		File file = new File(filePath);
		try {
			fin = new FileInputStream(file);
			fileContent = new byte[(int) file.length()];
			fin.read(fileContent);

		} catch (FileNotFoundException e) {
			Log.e(TAG,
					"VvmFileUtils.fileBuffter() - file not found .", e);
		} catch (IOException e) {
			Log.e(TAG,
					"VvmFileUtils.fileBuffter() Exception while reading the file ", e);
		} finally {
			try {
				// releases resources if needed
				if (fin != null) {
					fin.close();
				}

			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		return fileContent;
		
	}
	
	
	/**
	 * Copies one file to another.
	 * 
	 * @param fileToCopy
	 *            (File != null) the file to copy.
	 * @param copiedFile
	 *            (File != null) the file to be copy to.
	 * @param chunkSize
	 *            (int) a desired chunk size in case files copy should be in
	 *            chunks.
	 * 
	 * @return (boolean) true in case the file has been copied successfully,
	 *         false otherwise.
	 */
	private static boolean copyFile(File fileToCopy, File copiedFile,
			int chunkSize) {
		// holds file to copy input stream
		FileInputStream fileToCopyInputStream = null;

		// holds copied file output stream
		FileOutputStream copiedFileOutputStream = null;

		// gets the size of the file to copy
		int fileToCopySize = (int) fileToCopy.length();

		// holds the total number of bytes which were copied
		int totalNumberOfCopiedBytes = 0;

		// in case the file should be copied without using chunks,
		// or the given chunk size is bigger than the file to copy size
		if (chunkSize <= 0 || chunkSize >= fileToCopySize) {
			// sets chunk size as the file to copy size
			chunkSize = fileToCopySize;
		}

		// holds file to copy data
		byte[] fileToCopyData = new byte[chunkSize];

		try {
			// gets file to copy input stream and copied file output stream
				fileToCopyInputStream = new FileInputStream(fileToCopy);
				copiedFileOutputStream = new FileOutputStream(copiedFile);

			// as long as not all data has been copied between files
			while (totalNumberOfCopiedBytes < fileToCopySize) {
				// in case the number of read bytes from the file to copy is
				// smaller than chunk size
				// (file's size in case chunks are not being used)
				int currentNumberOfReadBytes = 0;
					if ((currentNumberOfReadBytes = fileToCopyInputStream.read(
							fileToCopyData, 0, chunkSize)) != chunkSize) {
						Log.e(TAG,
								"VvmFileUtils.copyFile() - could not perform file copy.");
						return false;
					}
					copiedFileOutputStream.write(fileToCopyData, 0, chunkSize);
					copiedFileOutputStream.flush();

				// updates the total number bytes which were copied
				totalNumberOfCopiedBytes += currentNumberOfReadBytes;
			}

			// file has been copied successfully
			return true;
		} catch (Exception e) {
			Log.e(TAG, "VvmFileUtils.copyFile() - could not perform file copy.", e);
			return false;
		} finally {
			try {
				// releases resources if needed
				if (fileToCopyInputStream != null) {
					fileToCopyInputStream.close();
				}
				if (copiedFileOutputStream != null) {
					copiedFileOutputStream.close();
				}
			} catch (Exception e) {
				Log.e(TAG, "VvmFileUtils.copyFile() - error releasing resources.", e);
			}
		}
	}

	/**
	 * Returns whether external storage can be write.
	 * 
	 * @return (boolean) true in case external storage exists on the device,
	 *         false otherwise.
	 */
	public static boolean isExternalStorageExist() {
		return Environment.MEDIA_MOUNTED.equals(Environment
				.getExternalStorageState());
	}

	/**
	 * Returns a file from application's internal (private) storage.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileName
	 *            (String != null) the name of the file.
	 * 
	 * @return (File) the file from application's internal (private) storage.
	 */
	public static File getInternalFile(Context context, String fileName) {
		return new File(context.getFilesDir(), fileName);
	}

	/**
	 * Returns a file from application's external storage.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileName
	 *            (String != null) the name of the file.
	 * 
	 * @return (File) the file from application's external storage.
	 */
	public static File getExternalFile(Context context, String fileName) {
		return new File(context.getExternalFilesDir(null), fileName);
	}

	/**
	 * Deletes a single file from application's internal (private) storage.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileNames
	 *            (String != null) the name of the file to delete.
	 * 
	 * @return (boolean) true in case the file was succesfully deleted, false
	 *         otherwise.
	 */
	public static boolean deleteInternalFile(Context context, String fileName) {
		if (fileName == null || fileName.length() == 0) {
			return false;
		}

		return context.deleteFile(fileName);
	}

	/**
	 * Deletes files from application's internal (private) storage.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileNames
	 *            (String[] != null) the names of the files to delete.
	 * 
	 * @return (int) the number of files actually deleted.
	 */
	public static int deleteInternalFiles(Context context, String[] fileNames) {
		// in case there are no files to delete, do nothing
		if (fileNames == null || fileNames.length == 0) {
			return 0;
		}

		// holds the number of files actually deleted
		int numberOfDeletedFiles = 0;

		// deletes all files and update the number of actually deleted files
		for (String fileName : fileNames) {
			numberOfDeletedFiles += deleteInternalFile(context, fileName) ? 1
					: 0;
		}

		// returns the number of actually deleted files
		return numberOfDeletedFiles;
	}

	/**
	 * Deletes a single file from application's external storage.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileNames
	 *            (String != null) the name of the file to delete.
	 * 
	 * @return (boolean) true in case the file was succesfully deleted, false
	 *         otherwise.
	 */
	public static boolean deleteExternalFile(Context context, String fileName) {
		if (fileName == null || fileName.length() == 0) {
			return false;
		}

		return new File(context.getExternalFilesDir(null), fileName).delete();
	}

	/**
	 * Deletes files from application's external storage.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param fileNames
	 *            (String[] != null) the names of the files to delete.
	 * 
	 * @return (int) the number of files actaully deleted.
	 */
	public static int deleteExternalFiles(Context context, String[] fileNames) {
		// in case there are no files to delete, do nothing
		if (fileNames == null || fileNames.length == 0) {
			return 0;
		}

		// holds the number of files actaully deleted
		int numberOfDeletedFiles = 0;

		// deletes all files and update the number of actually deleted files
		for (String fileName : fileNames) {
			numberOfDeletedFiles += deleteExternalFile(context, fileName) ? 1
					: 0;
		}

		// returns the number of actually deleted files
		return numberOfDeletedFiles;
	}

	// TODO - Royi - TEMPORARY METHOD ONLY!
	/**
	 * delete all files from the given folder
	 * 
	 * @author istelman
	 * @param folderPath
	 * @return
	 */
	public static int deleteAllFilesFromFolder(String folderPath) {
		File folder = new File(folderPath);
		if (!folder.exists() || !folder.isDirectory()) {
			return 0;
		}
		int i = 0;
		File[] files = folder.listFiles();
		for (File file : files) {
			if (file.delete()) {
				i++;
			}
		}
		return i;
	}

	/**
	 * 
	 */
	public static boolean renameFile(String oldFileName, String newFileName) {
		File file = new File(oldFileName);
		File newFile = new File(newFileName);
		if (file.exists() && !newFile.exists()) {
			return file.renameTo(newFile);
		}
		return false;
	}

	/**
	 * @author istelman
	 * @param context
	 * @param object
	 *            - the object to be saved
	 * @param fileName
	 *            - the file name in which the object would be saved
	 * @return - true if save was successful
	 */
	public static boolean saveSerializable(Context context, Object object,
			String fileName) {

		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(object);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * @author istelman
	 * @param context
	 * @param fileName
	 *            - the file name from which to load the object
	 * @return - the loaded object if loading was successful. null otherwise.
	 */
	public static Object loadSerializable(Context context, String fileName) {

		File file = context.getFileStreamPath(fileName);
		if (!file.exists()) {
			return null;
		}

		FileInputStream fis = null;
		ObjectInputStream ois = null;
		Object object = null;
		try {
			fis = context.openFileInput(fileName);
			ois = new ObjectInputStream(fis);
			object = ois.readObject();
			ois.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		return object;
	}
}