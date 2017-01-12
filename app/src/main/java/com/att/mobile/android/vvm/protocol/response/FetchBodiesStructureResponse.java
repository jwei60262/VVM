package com.att.mobile.android.vvm.protocol.response;

import java.util.ArrayList;

import com.att.mobile.android.vvm.protocol.BodyStructure;

/**
 * Represent a response returned by the Fetch body-structure command (support multiple uid's).
 * fetch body-structure command returns structure of all parts in each message.
 * 
 * e.g.
 * 		* 2 FETCH (UID 2485 BODYSTRUCTURE (("TEXT" "PLAIN" ("CHARSET" "iso-8859-1" "X-SKIP_COMP" "yes" "X-ALU-COMP-REASON" "VTT") NIL NIL "8BIT" 86 0 NIL ("IN
 *		LINE" NIL) ("NIL"))("audio" "amr" ("X-CODEC" "amr" "X-DURATION" "4") NIL "Voice File" "BASE64" 11492 NIL ("inline" ("filename" "voice.amr")) ("NIL"))
 *		"MIXED" ("BOUNDARY" "============>>AnyPath 1321127092<<============") NIL ("NIL")))
 *	 	* 3 FETCH (UID 2487 BODYSTRUCTURE (("TEXT" "PLAIN" ("CHARSET" "iso-8859-1" "X-SKIP_COMP" "yes" "X-ALU-COMP-REASON" "VTT") NIL NIL "8BIT" 84 0 NIL ("IN
 *		LINE" NIL) ("NIL"))("TEXT" "PLAIN" ("CHARSET" "ascii" "X-SKIP_COMP" "yes") NIL NIL "7BIT" 92 0 NIL ("INLINE" NIL) ("NIL"))("audio" "amr" ("X-CODEC" "a
 *		mr"	"X-DURATION" "12") NIL "Voice File" "BASE64" 0 NIL ("inline" ("filename" "voice.amr")) ("NIL")) "MIXED" ("BOUNDARY" "============>>AnyPath 1289981
 *		557<<============") NIL ("NIL"))) 
 * 
 * @author bobermeister
 *
 */
public class FetchBodiesStructureResponse extends IMAP4Response  {

	// a list of BodyStructure objects 
	protected ArrayList<BodyStructure> bodyStructureList = new ArrayList<BodyStructure>();
	
	public FetchBodiesStructureResponse(int result) {
		super(result);
	}
	
	public FetchBodiesStructureResponse() {
		super();
	}
	
	public ArrayList<BodyStructure> getBodyStructureList() {
		return bodyStructureList;
	}
}
