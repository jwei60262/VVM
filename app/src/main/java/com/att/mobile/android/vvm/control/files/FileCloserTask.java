package com.att.mobile.android.vvm.control.files;

import com.att.mobile.android.vvm.model.db.MessageDo;



/**
 * TODO
 */
public class FileCloserTask
{
	//holds file's message ID-UID pair
	MessageDo messageDo;
	
	//holds the name of the file
	String fileName;
	
	//holds the size of all message files so far
	int messageFilesSize;

	/**
	 * TODO
	 * 
	 * @param idUidPair
	 * @param messageFileIndex
	 * @param fileName
	 * @param messageFilesSize
	 * @param chunkNum
	 * @param lastChunk
	 * @param data
	 */
	public FileCloserTask(MessageDo messageDo, String fileName, int messageFilesSize)
	{
		this.messageDo = messageDo;
		this.fileName = fileName;
		this.messageFilesSize = messageFilesSize;
	}
}