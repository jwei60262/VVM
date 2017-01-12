package com.att.mobile.android.vvm.protocol;

import java.util.ArrayList;

/**
 * respresent a full VVM message bodystructure divided into a body parts list
 * the class is build by parsing a fetch bodystructure response from the server.
 * currently the bodystructure is used to identify the position of the vioce attachment in the message body.
 * the body structure will be used in the future to suport a full deep parsing of the bodystructure response to represent the full picture a message structure including inline messages.
 * @author ldavid
 *
 */
public class BodyStructure {

	private ArrayList<BodyPart> bodyParts = new ArrayList<BodyPart>();
	private long uid;

	public BodyStructure(){
	}

	public BodyStructure(long uid){
		this.uid = uid;
	}
	
	public long getUid() {
		return uid;
	}

	public BodyPart getBodyPart(int index) {
		return bodyParts.get(index);
	}

	public ArrayList<BodyPart> getBodyParts() {
		return bodyParts;
	}

	public void addBodyPart(BodyPart bodyPart) {
		bodyParts.add(bodyPart);
	}

	public int size() {
		return bodyParts.size();
	}
}