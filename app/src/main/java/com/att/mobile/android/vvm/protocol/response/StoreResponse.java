/**
 * 
 */
package com.att.mobile.android.vvm.protocol.response;

import java.util.HashSet;
import java.util.Set;

/**
 * @author istelman
 *
 */
public class StoreResponse extends IMAP4Response {
	
	
	private Set<Long> deletedUids;
	private Set<Long> readUids;
	private Set<Long> skippedUids;
	
	/**
	 * 
	 */
	public StoreResponse() {
		super();
		deletedUids = new HashSet<Long>();
		readUids = new HashSet<Long>();
		skippedUids = new HashSet<Long>();
	}
	/**
	 * @param result
	 * @param description
	 */
	public StoreResponse(int result, String description) {
		super(result, description);
		deletedUids = new HashSet<Long>();
		readUids = new HashSet<Long>();
		skippedUids = new HashSet<Long>();
	}
	/**
	 * @param result
	 */
	public StoreResponse(int result) {
		super(result);
		deletedUids = new HashSet<Long>();
		readUids = new HashSet<Long>();
		skippedUids = new HashSet<Long>();
	}

	
	
	/**
	 * @return the deletedUids
	 */
	public Set<Long> getDeletedUids() {
		return deletedUids;
	}
	/**
	 * @param deletedUids the deletedUids to set
	 */
	public void setDeletedUids(Set<Long> deletedUids) {
		this.deletedUids = deletedUids;
	}
	/**
	 * @return the readUids
	 */
	public Set<Long> getReadUids() {
		return readUids;
	}
	
	/**
	 * @return the skippedUids
	 */
	public Set<Long> getSkippedUids() {
		return skippedUids;
	}
	
	/**
	 * @param readUids the readUids to set
	 */
	public void setReadUids(Set<Long> readUids) {
		this.readUids = readUids;
	}
	
	/**
	 * @param skippedUids the skippedUids to set
	 */
	public void setSkippedUids(Set<Long> skippedUids) {
		this.skippedUids = skippedUids;
	}
	
	public void addRead(long uid){
		readUids.add(uid);
	}
	
	public void addDeleted(long uid){
		deletedUids.add(uid);
	}
	
	public void addSkipped(long uid){
		skippedUids.add(uid);
	}
}
