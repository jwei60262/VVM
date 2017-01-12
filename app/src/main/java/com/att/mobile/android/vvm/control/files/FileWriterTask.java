package com.att.mobile.android.vvm.control.files;

import java.util.Arrays;

import com.att.mobile.android.vvm.model.db.MessageDo;



/**
 * TODO
 */
public class FileWriterTask
{
	//holds file's message ID-UID pair
	private MessageDo messageDo;
	
	//holds the index of the file for the message it belongs to (1st file, 2nd file etc.)
	private int messageFileIndex;
	
	//holds the name of the file
	private String fileName;
	
	//holds the size of all message files so far
	private int messageFilesSize;

	//holds the chunk number for the current file
	private int chunkNumber;

	//holds current file's actual chunk data
	private byte[] data;
	
	// for concat amr files
	private boolean skipHeader = false;

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
	public FileWriterTask(MessageDo messageDo, int messageFileIndex, String fileName, int messageFilesSize, byte[] data, int chunkNumber)
	{
		this.messageDo = messageDo;
		this.messageFileIndex = messageFileIndex;
		this.fileName = fileName;
		this.messageFilesSize = messageFilesSize;
		this.chunkNumber = chunkNumber;
		this.data = Arrays.copyOf(data, data.length);
		
		//in case first 6 bytes should be skipped (AMR header)
		if(messageFileIndex != 1 && chunkNumber == 1)
		{
			skipHeader = true;
		}
	}

	public MessageDo getMessageDo() {
		return messageDo;
	}

	public void setMessageDo(MessageDo messageDo) {
		this.messageDo = messageDo;
	}

	public int getMessageFileIndex() {
		return messageFileIndex;
	}

	public void setMessageFileIndex(int messageFileIndex) {
		this.messageFileIndex = messageFileIndex;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public int getMessageFilesSize() {
		return messageFilesSize;
	}

	public void setMessageFilesSize(int messageFilesSize) {
		this.messageFilesSize = messageFilesSize;
	}

	public int getChunkNumber() {
		return chunkNumber;
	}

	public void setChunkNumber(int chunkNumber) {
		this.chunkNumber = chunkNumber;
	}

	public byte[] getData() {
		return  Arrays.copyOf(data, data.length);
	}

	public void setData(byte[] data) {
		this.data = Arrays.copyOf(data, data.length);
	}

	public boolean isSkipHeader() {
		return skipHeader;
	}

	public void setSkipHeader(boolean skipHeader) {
		this.skipHeader = skipHeader;
	}
}