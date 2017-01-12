package com.att.mobile.android.vvm.protocol;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.control.network.NetworkHandler;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.METADATA_VARIABLES;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.protocol.response.FetchBodiesStructureResponse;
import com.att.mobile.android.vvm.protocol.response.FetchHeadersResponse;
import com.att.mobile.android.vvm.protocol.response.FetchResponse;
import com.att.mobile.android.vvm.protocol.response.MetaDataResponse;
import com.att.mobile.android.vvm.protocol.response.StoreResponse;

public class IMAP4Parser {

	private final static byte SPACE = 32;
	private final static char CHAR_OPEN_BRACKET = '(';
	private final static char CHAR_CLOSE_BRACKET = ')';
	private final static String NULL_STRING = "";
	private final static String STR_FETCH = "fetch";
	private final static String STR_UID = "uid";
	private final static String STR_FLAG_SEEN = "\\seen";
	private final static String STR_FLAG_DELETED = "\\deleted";
	private final static String STR_FLAG_TUISKIPPED = "tuiskipped";
	private final static String STR_FLAG_URGENT = "priority";
	private final static String STR_FLAG_DSN = "dsn";
	private final static String STR_DATE = "date";
	private final static char CHAR_QUESTION_MARK = '?';
	private final static String STR_ENCODING_INDICATOR = "=?";
	private final static String STR_HEADER_CLI_NUMBER = "x-cli_number";
	private final static String STR_HEADER_X_ALU_PREVIOUS_UID = "x-alu-previous-uid";
	private final static String STR_HEADER_X_ALU_PREVIOUS_UID_REASON = "x-alu-previous-uid-reason";
	private final static String STR_HEADER_X_ALU_COMP_REASON = "x-alu-comp-reason";
	private static final String TAG = "IMAP4Parser";


	private NetworkHandler networkHandler;

	public IMAP4Parser(Context context, NetworkHandler networkHandler) {

		this.networkHandler = networkHandler;
	}

	/**
	 * Parse the headers records and extract the header data.
	 * 
	 * @param firstLine
	 * @return
	 * @throws IOException
	 *             ------------------------------------------------------------
	 *             --------------------------- fetch header response example: 3
	 *             FETCH (UID 141 FLAGS (\Seen) BODY[HEADER.FIELDS (DATE FROM
	 *             CONTENT-TYPE MESS AGE-CONTEXT CONTENT-DURATION)] {223}
	 *             Content-Type: Multipart/voice-message;
	 *             boundary="------------Boundary-00=_1YSEH 9ECC5GNTT4D7TH0"
	 *             From: message@vicom.com Content-Duration: 4 Message-Context:
	 *             voice-message Date: Wed, 16 Apr 2008 08:30:01 +0000 (UTC)
	 *             X-CNS-Message-Context: voice-message X-CNS-Media-Size:
	 *             voice=9sec;
	 *             --------------------------------------------------
	 *             -------------------------------------
	 */
	public Message parseHeader(FetchHeadersResponse response, String firstLine, Context context) throws IOException, SSLException, ParseException {
		Message resultMessage = new Message();

		Vector<String> headerData = unfoldHeader(firstLine);
		String nextHeaderLine;
		String nextHeaderLineLower;
		int index = -1;
		String tempStr = null;

		for (int i = 0; i < headerData.size(); i++) {
			nextHeaderLine = (String) headerData.elementAt(i);
			nextHeaderLineLower = toLowerCase(nextHeaderLine);
			Logger.d(TAG, "IMAP4Parser.parseHeader() received header = " + nextHeaderLineLower);
			if (nextHeaderLine.length() > 0 && nextHeaderLine.charAt(0) == '*' && (index = nextHeaderLineLower.indexOf(STR_FETCH)) >= 0) {
				index = nextHeaderLineLower.indexOf(STR_UID);
				if (index >= 0) {
					index += STR_UID.length() + 1;

					resultMessage.setUid(Long.valueOf(nextHeaderLine.substring(index, nextHeaderLine.indexOf(SPACE, index))).longValue());
				}
				// message seen means it has been read/heard and saved to
				// archive
				if (nextHeaderLineLower.contains(STR_FLAG_SEEN)) {
					resultMessage.setRead(true);
				}
				// TUISkipped means it has only been read/heard
				if (nextHeaderLineLower.contains(STR_FLAG_TUISKIPPED)) {
					resultMessage.setTuiskipped(true);
				}
				// is prioritized as urgent
				if (nextHeaderLineLower.contains(STR_FLAG_URGENT)) {
					resultMessage.setUrgentStatus(Message.UrgentState.URGENT);
				}
				if (nextHeaderLineLower.contains(STR_FLAG_DSN)) {
					resultMessage.setDeliveryStatus(true);
				}
			} else if (nextHeaderLineLower.startsWith(STR_DATE)) {
				resultMessage.setDate(nextHeaderLine.substring(STR_DATE.length() + 1).trim());
			} else if (nextHeaderLineLower.startsWith(STR_HEADER_CLI_NUMBER)) {
				if (nextHeaderLine.length() > STR_HEADER_CLI_NUMBER.length()) {
					tempStr = nextHeaderLine.substring(STR_HEADER_CLI_NUMBER.length() + 1).trim();

					String phoneNumber = (TextUtils.isEmpty(tempStr) ? NULL_STRING : tempStr);
					
					// no need to convert - just leave phone number as it come
					phoneNumber = Utils.convertPhoneNumberToE164(phoneNumber);
					resultMessage.setSenderPhoneNumber(phoneNumber);
				}
			}  else if (nextHeaderLineLower.startsWith(STR_HEADER_X_ALU_PREVIOUS_UID_REASON)) {
				Logger.d(TAG, "IMAP4Parser.parseHeader() STR_HEADER_X_ALU_PREVIOUS_UID_REASON = " + nextHeaderLineLower);
			}  else if (nextHeaderLineLower.startsWith(STR_HEADER_X_ALU_COMP_REASON)) {
				Logger.d(TAG, "IMAP4Parser.parseHeader() STR_HEADER_X_ALU_COMP_REASON = " + nextHeaderLineLower);
			} 
			else if (nextHeaderLineLower.startsWith(STR_HEADER_X_ALU_PREVIOUS_UID)) {
				// x-alu-previous-uid: inbox-2342324532-45
				if (nextHeaderLine.length() > STR_HEADER_X_ALU_PREVIOUS_UID.length()) {
					int start = nextHeaderLine.lastIndexOf("-");
					if (start > 0 && start + 1 < nextHeaderLine.length()) {
						tempStr = nextHeaderLine.substring(start + 1).trim();
						resultMessage.setPreviousUid(Long.parseLong(tempStr));
						Logger.d(TAG, "IMAP4Parser.parseHeader() set message previous uid " + resultMessage.getPreviousUid()
								+ " to message " + resultMessage.getUid());
					}
				}
			}

			nextHeaderLine = null;
			nextHeaderLineLower = null;
		}
		headerData.removeAllElements();
		headerData = null;

		return resultMessage;
	}
	

	/**
	 * Since MIME Header can contain line folding we first undo the header
	 * folding. The unfolded headEr is stored in a ArrayList that each entry is
	 * a single header line.
	 */
	private Vector<String> unfoldHeader(String firstLine) throws IOException, SSLException {
		Vector<String> headerData = new Vector<String>();
		int count = 0;
		String buffer = firstLine;
		int octets = buffer.length() + 2;
		while (true) {
			// If the input starts with space of \t the it is a folded line and
			// we
			// are concatenating it to the previous input
			if (buffer.startsWith(" ") || buffer.startsWith("\t")) {
				headerData.setElementAt(headerData.elementAt(count - 1) + " " + buffer.trim(), count - 1);
			}
			// If the input is not empty, add it to the Elements ArrayList
			else if (!(buffer.equals(""))) {
				headerData.addElement(buffer);
				count++;
			}
			// If the line starts with ')', we reach the end of the Header input
			if (buffer.indexOf(CHAR_CLOSE_BRACKET) == 0) {
				break;
			}

			buffer = new String(networkHandler.receiveNextData());
			octets = octets + buffer.length() + 2;// add the octets of the input
			// + 2 (\r\n).
			Logger.d(TAG, "unfoldHeader() received <- " + buffer);
		}
		buffer = null;
		return headerData;
	}

	/**
	 * convert next line data to lower case data
	 * 
	 * @param genCase
	 * @return
	 */
	protected static String toLowerCase(String genCase) {
		String retVal = null;
		if (genCase != null) {
			retVal = genCase.toLowerCase();

			try {
				int currReplacementIndex = 0;
				int replacementStartPoint = 0;
				int replacementEndPoint = 0;

				currReplacementIndex = retVal.indexOf(STR_ENCODING_INDICATOR, currReplacementIndex);

				while (currReplacementIndex != -1) {
					currReplacementIndex += 2;
					replacementStartPoint = retVal.indexOf(CHAR_QUESTION_MARK, currReplacementIndex);

					if (replacementStartPoint > -1) {
						replacementStartPoint = retVal.indexOf(CHAR_QUESTION_MARK, replacementStartPoint + 1);
						replacementEndPoint = retVal.indexOf(CHAR_QUESTION_MARK, replacementStartPoint + 1);

						if (replacementStartPoint < retVal.length() && replacementStartPoint < genCase.length()
								&& replacementEndPoint < genCase.length() && replacementEndPoint < retVal.length()) {

							retVal = retVal.substring(0, replacementStartPoint);

							if (replacementEndPoint == -1) {
								retVal += genCase.substring(replacementStartPoint);
							} else {
								retVal += genCase.substring(replacementStartPoint, replacementEndPoint);
								retVal += retVal.substring(replacementEndPoint);
							}
						}
					} else {
						break;
					}

					currReplacementIndex = retVal.indexOf(STR_ENCODING_INDICATOR, currReplacementIndex);
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		return retVal;
	}

	/**
	 * Parse the headers records and extract the header data.
	 * 
	 * @param firstLine
	 * @return
	 * @throws IOException
	 *             ------------------------------------------------------------
	 *             --------------------------- fetch header response example: 11
	 *             FETCH (FLAGS (\\Recent \\Deleted New QMboxSize QMsgCount
	 *             SenderOffComplex Voice PVMDelivered Accessed \\Seen) UID 22)
	 *             --
	 *             ------------------------------------------------------------
	 *             -------------------------
	 */
	public void parseStore(StoreResponse response, String firstLine) throws IOException, ParseException {
		try {
			String nextHeaderLineLower = firstLine.toLowerCase();
			int index = -1;

			if (nextHeaderLineLower.length() > 0 && nextHeaderLineLower.charAt(0) == '*' && (index = nextHeaderLineLower.indexOf(STR_FETCH)) >= 0) {
				long uid = -1;
				index = nextHeaderLineLower.indexOf(STR_UID);
				if (index >= 0) {
					index += STR_UID.length() + 1;

					uid = Long.valueOf(nextHeaderLineLower.substring(index, nextHeaderLineLower.indexOf(CHAR_CLOSE_BRACKET, index)));
				}

				if (uid != -1) {
					// message seen means it has been read/heard and saved to
					// archive
					if (nextHeaderLineLower.contains(STR_FLAG_SEEN)) {
						response.addRead(uid);
					}
					// message deleted means this message will be deleted at the
					// next expunge
					if (nextHeaderLineLower.contains(STR_FLAG_DELETED)) {
						response.addDeleted(uid);
					}
					// message skipped means this is a new message
					// but we want to get an MWI for these messages
					if (nextHeaderLineLower.contains(STR_FLAG_TUISKIPPED)) {
						response.addSkipped(uid);
					}

				}
			}

			nextHeaderLineLower = null;

		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			throw new ParseException(e.getMessage(), 0);
		}
	}

	/**
	 * Confirm that the response is not an ALU link and set messageTranscription
	 * 
	 * @param responseStr
	 * @return FetchResponse filled with messageTranscription
	 *  
	 */
	protected FetchResponse parseTranscription(String responseStr) {
		FetchResponse response = new FetchResponse();
		
		// check if text is not an ALU link - we don't want the user to see it
		// "Additional information on .lvp audio files can be found at http://www.alcatel-lucent.com/mvp"
		if (!responseStr.equals(Constants.ALU_LINK_TEXT)) {
			// extract the text message and ignore the first command line.
			// response sample: 
			// 		* 1 FETCH (UID 157 BODY[2] {20266}
			//		This is a text message
			int start = responseStr.indexOf('}');
			int end = responseStr.lastIndexOf(')');
			if (end == -1) {
				end = responseStr.length()-1;
			}
			response.setMessageTranscription(responseStr.substring(start+1, end));
		}
		
		return response;
	}
	
	/**
	 * Parse the response of 'fetch bodystructure' call into FetchBodiesStructureResponse object. 
	 * 
	 * @param responseStr
	 * 		The response as returned by 'fetch bodystructure', e.g.:
	 * 		* 2 FETCH (UID 2485 BODYSTRUCTURE (("TEXT" "PLAIN" ("CHARSET" "iso-8859-1" "X-SKIP_COMP" "yes" "X-ALU-COMP-REASON" "VTT") NIL NIL "8BIT" 86 0 NIL ("IN
	 *		LINE" NIL) ("NIL"))("audio" "amr" ("X-CODEC" "amr" "X-DURATION" "4") NIL "Voice File" "BASE64" 11492 NIL ("inline" ("filename" "voice.amr")) ("NIL"))
	 *		"MIXED" ("BOUNDARY" "============>>AnyPath 1321127092<<============") NIL ("NIL")))
	 *	 	* 3 FETCH (UID 2487 BODYSTRUCTURE (("TEXT" "PLAIN" ("CHARSET" "iso-8859-1" "X-SKIP_COMP" "yes" "X-ALU-COMP-REASON" "VTT") NIL NIL "8BIT" 84 0 NIL ("IN
	 *		LINE" NIL) ("NIL"))("TEXT" "PLAIN" ("CHARSET" "ascii" "X-SKIP_COMP" "yes") NIL NIL "7BIT" 92 0 NIL ("INLINE" NIL) ("NIL"))("audio" "amr" ("X-CODEC" "a
	 *		mr"	"X-DURATION" "12") NIL "Voice File" "BASE64" 0 NIL ("inline" ("filename" "voice.amr")) ("NIL")) "MIXED" ("BOUNDARY" "============>>AnyPath 1289981
	 *		557<<============") NIL ("NIL"))) 
	 */
	protected FetchBodiesStructureResponse parseBodiesStructure(String responseStr) {
		FetchBodiesStructureResponse response = new FetchBodiesStructureResponse();
		
		String[] bodiesStructureStr = responseStr.split("\\*");
		for(String bodyStructureStr : bodiesStructureStr) {
			if (bodyStructureStr != null && bodyStructureStr.indexOf("BODYSTRUCTURE") != -1) {
				BodyStructure bodyStructure = parseBodyStructure(bodyStructureStr);						
				response.getBodyStructureList().add(bodyStructure);
			}
		}
		return response;
	}
	
	/**
	 * Parse the body-structure string into BodyStructure object. 
	 * 
	 * @param bodyStructureStr
	 * 		The bodystructure as returned by 'fetch bodystructure', e.g.:
	 * 		* 2 FETCH (UID 2485 BODYSTRUCTURE (("TEXT" "PLAIN" ("CHARSET" "iso-8859-1" "X-SKIP_COMP" "yes" "X-ALU-COMP-REASON" "VTT") NIL NIL "8BIT" 86 0 NIL ("IN
	 *		LINE" NIL) ("NIL"))("audio" "amr" ("X-CODEC" "amr" "X-DURATION" "4") NIL "Voice File" "BASE64" 11492 NIL ("inline" ("filename" "voice.amr")) ("NIL"))
	 *		"MIXED" ("BOUNDARY" "============>>AnyPath 1321127092<<============") NIL ("NIL")))
	 */
	protected BodyStructure parseBodyStructure(String bodyStructureStr) {
		
		String fetchStr = stripResponseString(bodyStructureStr);
		int bodyStructureIndex = fetchStr.indexOf("BODYSTRUCTURE");
		String uidPair = fetchStr.substring(0, bodyStructureIndex);
		String uid = uidPair.split("\\s+")[1].trim();
		BodyStructure bodyStructure = new BodyStructure(Long.parseLong(uid));
		
		String partsStr = stripResponseString(fetchStr.substring(bodyStructureIndex));
		int numOfOpenBrackets = 0;
		StringBuilder part = new StringBuilder();

		for(int i=0; i<partsStr.length(); i++) {
			char c = partsStr.charAt(i);
			if (c == CHAR_OPEN_BRACKET) {
				numOfOpenBrackets++;
			}
			else if (c == CHAR_CLOSE_BRACKET) {
				numOfOpenBrackets--;
			}
			part.append(c);
			
			// end of part (part includes outer brackets)
			if (numOfOpenBrackets == 0 && part.toString().startsWith(Character.toString(CHAR_OPEN_BRACKET))) {
				BodyPart bodyPart = parseBodyPart(part.toString());
				bodyStructure.getBodyParts().add(bodyPart);
				
				// re-initiate part (for the next iteration)
				part = new StringBuilder();
			}
		}			
		
		return bodyStructure;
	}
	
	/**
	 * Parse the body-part string into BodyPart object. 
	 * 
	 * @param bodyPartStr
	 * 		The part as returned by 'fetch bodystructure', e.g.:
	 * 		("TEXT" "PLAIN" ("CHARSET" "ascii" "X-SKIP_COMP" "yes") NIL NIL "7BIT" 92 0 NIL ("INLINE" NIL) ("NIL"))
	 */
	protected BodyPart parseBodyPart(String bodyPartStr) {
		String variable = null, value = null;
		
		String partStr = stripResponseString(bodyPartStr);
		BodyPart bodyPart = new BodyPart(partStr);

		int xHeaderStartIndex = partStr.indexOf(CHAR_OPEN_BRACKET);
		int xHeaderEndIndex = partStr.indexOf(CHAR_CLOSE_BRACKET);
		
		// extract content-type
		String contentType = partStr.substring(0, xHeaderStartIndex);
		String[] contentTypeParts = contentType.split("\\s+");
		contentType = stripQuotedString(contentTypeParts[0]);
		if (contentTypeParts.length > 1) {
			contentType = contentType + "/" + stripQuotedString(contentTypeParts[1]);
		}
		bodyPart.setContentType(contentType);
		
		// parse x-headers
		String xHeaderStr = stripResponseString(partStr.substring(xHeaderStartIndex, xHeaderEndIndex+1));
		StringTokenizer st = new StringTokenizer(xHeaderStr, " ");
		while (st.hasMoreTokens()) {
			try {
				variable = st.nextToken().trim();  // comes in format "<value>"
				if (st.hasMoreTokens()) {
					value = st.nextToken().trim(); 	// comes in format "<value>"
				}
			} catch (NoSuchElementException e) {
				Logger.d(TAG, e.getMessage());
			}
			if (variable != null && value != null) {
				if (stripQuotedString(variable).equalsIgnoreCase("x-alu-comp-reason")) {
					value = stripQuotedString(value);
					bodyPart.setIsVttAluReason(value.equalsIgnoreCase("vtt"));
				}
			}
		}
		
		return bodyPart;
	}
	
	/**
	 * request a3 GETMETADATA INBOX <variable> <variable> response METADATA
	 * "INBOX" (<variable> "<value>" <variable> "<value>" <variable> "<value>")
	 * a3 OK GETMETADATA complete
	 * 
	 * the response format is: * METADATA "INBOX" (<variable> "<value>") parses
	 * a get meta data response from the server , the response may contains 1 or
	 * more variable-value couples
	 * 
	 * @param response
	 * @return
	 */
	protected MetaDataResponse parseMetaData(String responseStr) {

		MetaDataResponse response = new MetaDataResponse();
		String variable = null, value = null;

		// find meta data variable indexes
		int start = responseStr.indexOf('(');
		int end = responseStr.lastIndexOf(')');
		String vars = responseStr.substring(start + 1, end);

		StringTokenizer st = new StringTokenizer(vars, " ");
		while (st.hasMoreTokens()) {

			try {
				variable = st.nextToken();
				value = st.nextToken(); // comes in format "<value>"
				if (value != null && value.length() > 2) {
					value = value.substring(1, value.length() - 1);
				}
			} catch (NoSuchElementException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			if (variable != null && value != null) {
				response.addVariable(variable, value);
			}
		}
		return response;
	}

	/**
	 * request a3 GETMETADATA INBOX <variable> <variable> response METADATA
	 * "INBOX" (<variable> "<value>" <variable> "<value>" <variable> "<value>")
	 * a3 OK GETMETADATA complete
	 * 
	 * the response format is: * METADATA "INBOX" (<variable> "<value>") parses
	 * a get meta data response from the server , the response may contains 1 or
	 * more variable-value couples
	 * 
	 * @param responseStr
	 * @return
	 */
	protected MetaDataResponse parseGreetingMetaData(String responseStr) {

		MetaDataResponse response = new MetaDataResponse();
		Pattern pattern = null;

		if (responseStr != null && !responseStr.equals("")) {
			Logger.d(TAG, "IMAP4Parser::parseGreetingMetaData => response string exists!");
			try {
				// strip response from (...)
				String vars = stripResponseString(responseStr);

				// get the stream as a strings array
				ArrayList<String> streams = getStreamsStrings(pattern, vars);

				// get the imap command as a string array
				ArrayList<String> imapCommands = getImapCommands(pattern, vars);

				// get key/value pairs of imapCommand and its stream
				HashMap<String, String> imapCommandStreamHashMap = createImapCommandStreamsHashMap(imapCommands, streams);

				// add variables to response's hashmap
				response.addVariablesRange(imapCommandStreamHashMap);
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}

		}

		return response;
	}

	/**
	 * Creates a merged hashmap out of 2 string arrays
	 * 
	 * @param imapCommands
	 *            imap keys strings array
	 * @param streams
	 *            stream strings array
	 * @return hashmap of merged value in the format of key=imapCommand
	 *         value=stream
	 */
	private HashMap<String, String> createImapCommandStreamsHashMap(ArrayList<String> imapCommands, ArrayList<String> streams) {

		HashMap<String, String> localHash = new HashMap<String, String>();
		for (int i = 0; i < imapCommands.size(); i++) {
			localHash.put(imapCommands.get(i), streams.get(i));
		}

		return localHash;
	}

	/**
	 * Finds all the imap command in the response string
	 * 
	 * @param pattern
	 *            Regular expression pattern for imap command
	 * @param vars
	 *            the string for matching
	 *            (/private/vendor/vendor.alu/messaging/Reco
	 * 
	 * @return strings array of matching substrings
	 */
	private ArrayList<String> getImapCommands(Pattern pattern, String vars) {
		pattern = Pattern.compile("/private/vendor/vendor.alu/messaging/Greetings/[A-Za-z]+");
		Matcher imapCommandMatcher = pattern.matcher(vars);
		ArrayList<String> imapCommands = new ArrayList<String>();
		String currentGroup = null;
		while (imapCommandMatcher.find()) {
			try {
				currentGroup = imapCommandMatcher.group();
				imapCommands.add(currentGroup);
			} catch (IllegalStateException e) {
				Log.e(TAG, "IMAP4Parser getImapCommands unsuccesful grouping" , e);
			}
		}

		// check for recorded name as well
		if (vars.contains(METADATA_VARIABLES.RecordedName)) {
			imapCommands.add(METADATA_VARIABLES.RecordedName);
		}

		return imapCommands;
	}

	/**
	 * Finds all the stream substrings
	 * 
	 * @param pattern
	 *            pattern Regular expression pattern for strem
	 * @param vars
	 *            the string for matching
	 * @return strings array of matching substrings
	 *         (/private/vendor/vendor.alu/messaging/RecordedName ~{5537}
	 */
	private ArrayList<String> getStreamsStrings(Pattern pattern, String vars) {
		Pattern streamsPattern = Pattern.compile("/private/vendor/vendor.alu/messaging/Greetings/.*AMR");
		String[] localStrArr = streamsPattern.split(vars);
		ArrayList<String> retArray = new ArrayList<String>();
		for (String stream : localStrArr) {
			if (stream != null && !stream.equals("")) {
				retArray.add(stream);
			}
		}

		Pattern nameStreamsPattern = Pattern.compile("/private/vendor/vendor.alu/messaging/RecordedName/.*AMR");
		String[] nameLocalStrArr = nameStreamsPattern.split(vars);

		for (String stream : nameLocalStrArr) {
			if (stream != null && !stream.equals("")) {
				retArray.add(stream);
			}
		}

		return retArray;
	}

	/**
	 * Removes the wrapping '(' ')' from the response string
	 * 
	 * @param responseStr
	 * @return
	 */
	private String stripResponseString(String responseStr) {
		// find meta data variable indexes
		int start = responseStr.indexOf('(');
		int end = responseStr.lastIndexOf(')');
		if (start != -1 && start != end) {
			return responseStr.substring(start + 1, end);
		}
		return responseStr;
	}

	/**
	 * Removes the wrapping '"' '"' from the response string
	 * 
	 * @param responseStr
	 * @return
	 */
	private String stripQuotedString(String responseStr) {
		int start = responseStr.indexOf('"');
		int end = responseStr.lastIndexOf('"');
		if (start != -1 && start != end) {
		return responseStr.substring(start + 1, end);
	}
		return responseStr;
	}
}