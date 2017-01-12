package com.att.mobile.android.vvm.watson;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.att.mobile.android.infra.network.TLSSocketFactory;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.ModelManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;


public class WatsonHandler {
	private static String accessToken = null;
	private static final String GET_TOKEN_URL = "https://api.att.com/oauth/token";
	private static final String GET_TRANSCRIPTION_URL = "https://api.att.com/speech/v3/speechToText";
//	private static final String client_ID = "lt0fwgrf6jgcju0r9byhdaupxuz5hrby";
//	private static final String client_Secret = "nczx20fhbwlteabn6ktjq54n4txexeym";
	private static final String client_ID = "dxngpbidevxl7zdxox7wducydooqd6q0";
	private static final String client_Secret = "eaawymjxawvdqjsia96huqr3bpilprpl";
	private  InputStream is;
	private  String json;
	private  JSONObject jObj;
	static WatsonHandler instance;
	private static Object lock = new Object();
	private static final String TAG = "WatsonHandler";
	private static final int RETRY_TIME = 60000;
	private static final int TIMEOUT_RETRY_TIME1 = 300000;
	private static final int TIMEOUT_RETRY_TIME2 = 1800000;
	private static final int TIMEOUT_CONNECTION = 3600000;
	private static final String PhoneTagStart = "_PHONE";
	private static final String PhoneTagEnd = "_END_PHONE";
	
	private static final int HTTP_EXPECTATION_FAILED = 417;
	
	public interface WiFiConnectivityInterface {
		
		public void requestWiFi();
	}
	public static void createInstance(Context context) {
		// thread safe singleton
		synchronized (lock) {
			if (instance == null && context != null) {
				instance = new WatsonHandler(context);
			}
		}
	}

	public static WatsonHandler getInstance() {
		if (instance == null) {
			Log.e(TAG,
					"WatsonHandler.getInstance() must call create instance before calling getInstance()");
		}
		return instance;
	}

	public Context context;


	private WatsonHandler(Context context) {

		this.context = context;
	}

	
	private int getTranscription(long messageID, String fileName) {

		Logger.d(TAG, "getTranscription() messageID=" + messageID + " fileName=" + fileName);

		HttpURLConnection urlConnection = null;
		FileInputStream fileIs = null;
		InputStream is = null;
		OutputStream out = null;

		String filePath = new StringBuilder(VVMApplication.getContext().getFilesDir().getPath()).append(File.separator).toString();
		String fullFilePath = new StringBuilder(filePath).append(fileName).toString();
		String transcription = null;
		int status_code = HttpURLConnection.HTTP_OK;
		File file = new File(fullFilePath);

		if (accessToken != null && (!ModelManager.getInstance().isMessagePendingForDelete(messageID))) {
			try {

				URL url = new URL(GET_TRANSCRIPTION_URL);
				urlConnection = (HttpURLConnection) url.openConnection();

				if (urlConnection instanceof HttpsURLConnection)
				{
					((HttpsURLConnection) urlConnection).setSSLSocketFactory(new TLSSocketFactory());
				}

				urlConnection.setConnectTimeout(TIMEOUT_CONNECTION);
				urlConnection.setReadTimeout(TIMEOUT_CONNECTION);
				urlConnection.setRequestMethod("POST");
				urlConnection.setDoInput(true);
				urlConnection.setDoOutput(true);
				urlConnection.setUseCaches(false);
				urlConnection.setChunkedStreamingMode(0); // for the default chunk length.

				urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
				urlConnection.setRequestProperty("Content-type", "audio/amr");
				urlConnection.setRequestProperty("Accept", "application/json");
				urlConnection.setRequestProperty("X-SpeechContext", "VoiceMail");
				urlConnection.setRequestProperty("X-Arg", "Language=en-us,NBest=0,FormatFlag=1");

				out = urlConnection.getOutputStream();

				fileIs = new FileInputStream(file);

				writeStream(out, fileIs);
				out.flush();
				

				urlConnection.connect();

				status_code = urlConnection.getResponseCode(); 
				Logger.d(TAG, "WatsonHandler.getTranscription()  status_code - " + status_code);

			} catch (SocketTimeoutException se) {
				status_code = HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
	    		Logger.d(TAG,	"WatsonHandler.getTranscription() ConnectTimeoutException status_code - " + status_code);
			} catch (Exception e) {
				status_code = Utils.isWiFiOn(context) ? HttpURLConnection.HTTP_GATEWAY_TIMEOUT : HttpURLConnection.HTTP_BAD_REQUEST;
				Logger.d(TAG, "WatsonHandler.getTranscription() Exception status_code - " + status_code);
			} finally {
				if ( fileIs != null ) {
					try { fileIs.close(); } catch (IOException e) { }
				}
				if ( out != null ) {
					try { out.close(); } catch (IOException e) { }
				}
			}			
			
			boolean simulateTranslError = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.SIMULATE_TRANSL_ERROR, Boolean.class, false);
			if (simulateTranslError) {
				String status_code_string = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.TranslErrorCode, String.class, "" + HttpURLConnection.HTTP_NOT_IMPLEMENTED);
				if (status_code_string.trim().equals("Unknown")) {
					status_code_string = "" + HttpURLConnection.HTTP_NOT_IMPLEMENTED;
				}
				status_code = Integer.parseInt(status_code_string.trim());
				Logger.d(TAG, "WatsonHandler.getTranscription() simulate Transcription error Error code - " + status_code);
			}

			if (status_code == HttpURLConnection.HTTP_OK) {

				BufferedReader reader = null;
				try {
					is = urlConnection.getInputStream();

					reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
					StringBuilder sb = new StringBuilder();
					String line = null;
					while ((line = reader.readLine()) != null) {
						sb.append(line + "\n");
					}

					json = sb.toString();
					jObj = new JSONObject(json).getJSONObject("Recognition");

					JSONArray venues = jObj.getJSONArray("NBest");
					double minConf = Double.parseDouble(ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.MinConfidence, String.class, Constants.DEFAULT_MIN_CONFIDENCE_LEVEL));
					Logger.d(TAG, "WatsonHandler.getTranscription() minimal shown Confidence - " + minConf);
					for (int i = 0; i < venues.length(); i++) {
						JSONObject venueObject = venues.getJSONObject(i);
						double conf = venueObject.getDouble("Confidence");
						Logger.d(TAG, "WatsonHandler.getTranscription() got transcription - " + venueObject.getString("ResultText") + " with Confidence - " + conf);
						if (conf >= minConf) {
							transcription = venueObject.getString("ResultText");
							Logger.d(TAG, "WatsonHandler.getTranscription() succeeded transcription - " + transcription);
							transcription = parsePhoneFromTranscription(transcription);
							Logger.d(TAG, "WatsonHandler.getTranscription() after parsing transcription - " + transcription);
						} else {
							transcription = context.getString(R.string.trascriptionErrorText);
						}
					}
				} catch (Exception e) {
					status_code = Utils.isWiFiOn(context) ? HttpURLConnection.HTTP_GATEWAY_TIMEOUT : HTTP_EXPECTATION_FAILED;
					Log.e("Buffer Error", "Error converting result " + e.toString());
				} finally {
					if (is != null) {
						try { is.close(); } catch (IOException e) { }
					}
					if (reader != null) {
						try { reader.close(); } catch (IOException e) { }
					}
				}
				if (needRequestWatson(messageID)) {
					if (TextUtils.isEmpty(transcription)) {
						transcription = context.getString(R.string.trascriptionErrorText);
					}
					long uid = ModelManager.getInstance().getMessageUID(messageID);
					ModelManager.getInstance().setMessageDetailsFromBodyText(instance.context, messageID, uid, transcription);
				}
			}

		}
		return status_code;
	}

	private void writeStream(OutputStream out, FileInputStream fileIs) throws IOException {
		int count;
		byte[] buf = new byte[1024];
		while ((count = fileIs.read(buf)) != -1) {
			out.write(buf, 0, count);
		}
	}
	
	private boolean isCanConnect(){
		
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		final android.net.NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
	    final android.net.NetworkInfo mobile = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

	    return (wifi.isAvailable() || mobile.isAvailable()); 
	 
	}
	
	private  int requestTranscription(long messageID, String fileName){
		int watson_status = ModelManager.getInstance().getMessageWatsonStatus(messageID);
		int status_code = HttpURLConnection.HTTP_OK;
		if(watson_status == Message.WatsonTranscriptionState.WAIT_FOR_RETRY || watson_status == Message.WatsonTranscriptionState.PENDING_RETRY){
			ModelManager.getInstance().setMessageWatsonStatus(messageID, Message.WatsonTranscriptionState.RETRY);
		} 	else if(watson_status == Message.WatsonTranscriptionState.PENDING_WAIT_FOR_RETRY){
			ModelManager.getInstance().setMessageWatsonStatus(messageID, Message.WatsonTranscriptionState.PENDING_RETRY);
		} else {
			ModelManager.getInstance().setMessageWatsonStatus(messageID, Message.WatsonTranscriptionState.WAIT_FOR_TRANSCRIPTION);
		}
		
		if(isCanConnect()){
			if(TextUtils.isEmpty(accessToken)){
				status_code = getInstance().getToken(messageID , fileName);
			}
			if(status_code == HttpURLConnection.HTTP_OK && (!TextUtils.isEmpty(accessToken))){
				status_code = getTranscription(messageID, fileName);
				// NSlesuratin - US53982: VMTT via Speech API - no API retry
				if(status_code != HttpURLConnection.HTTP_OK){
					ModelManager.getInstance().setMessageWatsonStatus(messageID, Message.WatsonTranscriptionState.TRANSCRIPTION_FAILED);
				}
//				if((status_code == HttpStatus.SC_BAD_REQUEST || status_code == HttpStatus.SC_UNAUTHORIZED) && ( watson_status != Message.WatsonTranscriptionState.RETRY)){
//					ModelManager.getInstance().setMessageWatsonStatus(messageID, Message.WatsonTranscriptionState.WAIT_FOR_RETRY);
//		    		Logger.d(TAG,	"WatsonHandler.requestTranscription() received status_code - "	+ status_code+ " scheduling retry");
//				}
			} 

		} else {
			status_code = -1;
		}
		return status_code;
	}
	private String parsePhoneFromTranscription(String transcription){
		int endposition = transcription.indexOf(PhoneTagEnd);
		if(endposition == -1){
			Logger.d(TAG,	"WatsonHandler.parsePhoneFromTranscription() before final transcription - "	+ transcription);
			transcription = transcription.replaceAll("_\\p{L}+", "");
			Logger.d(TAG,	"WatsonHandler.parsePhoneFromTranscription() final transcription - "	+ transcription);
			return transcription;
		} else {
			StringBuilder builder = new StringBuilder();
			int startposition = transcription.indexOf(PhoneTagStart);
			String phonenumber = transcription.substring(startposition + PhoneTagStart.length()+1 , endposition);
			Logger.d(TAG,	"WatsonHandler.parsePhoneFromTranscription() phonenumber - "	+ phonenumber);
			phonenumber = phonenumber.replace(" ", "");
			transcription = builder.append(transcription.substring(0, startposition)).append(phonenumber).append(transcription.substring(endposition+PhoneTagEnd.length())).toString();
			Logger.d(TAG,	"WatsonHandler.parsePhoneFromTranscription() parsing transcription - "	+ transcription);
			transcription = parsePhoneFromTranscription(transcription);
		}
		Logger.d(TAG,	"WatsonHandler.parsePhoneFromTranscription() before final transcription - "	+ transcription);
		transcription = transcription.replaceAll("_\\p{L}+", "");
		Logger.d(TAG,	"WatsonHandler.parsePhoneFromTranscription() final transcription - "	+ transcription);
		return transcription;
	
	}
	private  void onHttpError(int status_code, long messageID, String fileName){
		int watson_status = ModelManager.getInstance().getMessageWatsonStatus(messageID);
		if(isCanConnect()){
			if((status_code == HttpURLConnection.HTTP_BAD_REQUEST || status_code == HttpURLConnection.HTTP_UNAUTHORIZED) && 
					(watson_status != Message.WatsonTranscriptionState.WAIT_FOR_RETRY) || watson_status == Message.WatsonTranscriptionState.RETRY ||
					 watson_status == Message.WatsonTranscriptionState.TRANSCRIPTION_FAILED ){
				Logger.d(TAG,	"WatsonHandler.onHttpError() messageID - "	+ messageID+ " reset Token ");
				resetToken();
				ModelManager.getInstance().setMessageTranscriptionError(messageID);
			} else if( status_code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT && watson_status != Message.WatsonTranscriptionState.RETRY){
				int rt = watson_status == Message.WatsonTranscriptionState.WAIT_FOR_TRANSCRIPTION ? TIMEOUT_RETRY_TIME1 : TIMEOUT_RETRY_TIME2;
				int mesSt = watson_status == Message.WatsonTranscriptionState.WAIT_FOR_TRANSCRIPTION ? Message.WatsonTranscriptionState.PENDING_WAIT_FOR_RETRY : Message.WatsonTranscriptionState.PENDING_RETRY;
				Logger.d(TAG,	"WatsonHandler.onHttpError() messageID - "	+ messageID+ " error = "+ status_code+ " scheduling retry in "+rt+"ms. Status = "+mesSt);
				scheduleRetry(messageID, fileName, rt, mesSt);
			} else if( watson_status != Message.WatsonTranscriptionState.RETRY && status_code != -1 && 	status_code != HTTP_EXPECTATION_FAILED){
				if(!ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.TokenRetryFail, Boolean.class, false)){
					ModelManager.getInstance().setSharedPreference(Constants.KEYS.SIMULATE_TOKEN_ERROR, false);
				}
				if(!ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.TranslRetryFail, Boolean.class, false)){
					ModelManager.getInstance().setSharedPreference(Constants.KEYS.SIMULATE_TRANSL_ERROR, false);
				}

				scheduleRetry(messageID, fileName, RETRY_TIME, Message.WatsonTranscriptionState.WAIT_FOR_RETRY );
			}
		}
	}
	
	private int getToken(long messageID, String fileName) {
		
		Logger.d(TAG, "getToken messageID=" + messageID + " fileName=" + fileName);

		HttpURLConnection urlConnection = null;
		OutputStream os = null;
		BufferedWriter writer = null;

		int status_code = HttpURLConnection.HTTP_OK;


		try {

			URL url = new URL(GET_TOKEN_URL);
			urlConnection = (HttpURLConnection) url.openConnection();

			if (urlConnection instanceof HttpsURLConnection)
			{
				((HttpsURLConnection) urlConnection).setSSLSocketFactory(new TLSSocketFactory());
			}

			urlConnection.setRequestMethod("POST");
			urlConnection.setDoOutput(true);

			// Add your data
			Uri.Builder builder = new Uri.Builder().appendQueryParameter("client_id", client_ID).appendQueryParameter("client_secret", client_Secret).appendQueryParameter("grant_type", "client_credentials").appendQueryParameter("scope", "SPEECH");
			String query = builder.build().getEncodedQuery();
			Logger.d(TAG, "WatsonHandler.getToken() query=" + query);

			os = urlConnection.getOutputStream();
			writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
			writer.write(query);
			writer.flush();

			// Execute HTTP Post Request
			urlConnection.connect();

			status_code = urlConnection.getResponseCode();

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			status_code = HTTP_EXPECTATION_FAILED;
			Logger.d(TAG, "WatsonHandler.getToken() UnsupportedEncodingException status_code - " + status_code);
			return status_code;
		} catch (IOException e) {
			e.printStackTrace();
			status_code = Utils.isWiFiOn(context) ? HttpURLConnection.HTTP_GATEWAY_TIMEOUT : HTTP_EXPECTATION_FAILED;
			Logger.d(TAG, "WatsonHandler.getToken() IOException status_code - " + status_code);
			return status_code;
		} catch (Exception e) {
			e.printStackTrace();
			status_code = Utils.isWiFiOn(context) ? HttpURLConnection.HTTP_GATEWAY_TIMEOUT : HTTP_EXPECTATION_FAILED;
			Logger.d(TAG, "WatsonHandler.getToken() Exception status_code - " + status_code);
			return status_code;
		} finally {
			if (writer != null) {
				try { writer.close(); } catch (IOException e) { }
			}
			if (os != null) {
				try { os.close(); } catch (IOException e) { }
			}
		}

		boolean simulateTokenError = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.SIMULATE_TOKEN_ERROR, Boolean.class, false);
		if (simulateTokenError) {
			String status_code_string = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.TokenErrorCode, String.class, "" + HttpURLConnection.HTTP_NOT_IMPLEMENTED);
			if (status_code_string.trim().equals("Unknown")) {
				status_code_string = "" + HttpURLConnection.HTTP_NOT_IMPLEMENTED;
			}
			status_code = Integer.parseInt(status_code_string.trim());
			Logger.d(TAG, "WatsonHandler.getToken() simulate Token error Error code - " + status_code);
		}

		if (status_code == HttpURLConnection.HTTP_OK) {

			BufferedReader reader = null;
			try {
				is = urlConnection.getInputStream();
				reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
				StringBuilder sb = new StringBuilder();
				String line = null;
				while ((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
				if (sb != null) {
					json = sb.toString();
				}
			} catch (Exception e) {
				status_code = Utils.isWiFiOn(context) ? HttpURLConnection.HTTP_GATEWAY_TIMEOUT : HTTP_EXPECTATION_FAILED;
				Log.e("Buffer Error", "Error converting result " + e.toString());
			} finally {
				if (is != null) {
					try { is.close(); } catch (IOException e) { }
				}
				if (reader != null) {
					try { reader.close(); } catch (IOException e) { }
				}
				if ( urlConnection != null ) {
					urlConnection.disconnect();
				}
			}
			// try parse the string to a JSON object
			if (json != null) {
				try {
					jObj = new JSONObject(json);
					accessToken = jObj.getString("access_token");
					Logger.d(TAG, "WatsonHandler.getToken() succeeded accessToken - " + accessToken);
				} catch (JSONException e) {
					Log.e("JSON Parser", "Error parsing data " + e.toString());
				}
			} else {
				Log.e("JSON Parser", "Error parsing data, json = null ");
			}
		}

		return status_code;
	}
	
	private  void scheduleRetry(final long messageID, final String fileName, int retryTime, int messageStatus) {
		ModelManager.getInstance().setMessageWatsonStatus(messageID, messageStatus);
		Logger.d(TAG,	"WatsonHandler.scheduleRetry() messageID - "	+ messageID+ " retry time = "+ retryTime+ "message status = "+messageStatus);
	    final Handler handler = new Handler();
	    Timer timer = new Timer();
	    TimerTask doAsynchronousTask = new TimerTask() {       
	        @Override
	        public void run() {
	            handler.post(new Runnable() {
	                public void run() {       
	                    try {
	                    	RunWatsonTranscriptionTask performBackgroundTask = new RunWatsonTranscriptionTask(messageID, fileName);
		                        performBackgroundTask.execute();
	                    } catch (Exception e) {
	                        Log.e("scheduleRetry", "Error execute RunWatsonTranscriptionTask " + e.toString());
	                    }
	                }
	            });
	        }
	    };
	    timer.schedule(doAsynchronousTask, retryTime); 
	}

	public static void resetToken(){
		accessToken = null;
		Logger.d(TAG, "WatsonHandler.resetToken()");
	}
	
	public static boolean needRequestWatson(long messageID){
		String transcription = ModelManager.getInstance().getMessageTranscription(messageID);
		Logger.d("WatsonHandler",
				"needRequestWatson messageID = "+ messageID + " existing transcription = "+transcription);
		Logger.d("WatsonHandler",
				"needRequestWatson ModelManager.getInstance().getClientSideTranscription() = "+ModelManager.getInstance().getClientSideTranscription());
		Logger.d("WatsonHandler",
				"needRequestWatson returning  "+(ModelManager.getInstance().getClientSideTranscription()&& !ModelManager.getInstance().isMessageHasTranscription(messageID)));
		
		return ModelManager.getInstance().getClientSideTranscription() && !ModelManager.getInstance().isMessageHasTranscription(messageID);
	}
	
	  private class RunWatsonTranscriptionTask extends AsyncTask<Void, Void, Integer> {
		  long messageID;
		  String fileName;
		public RunWatsonTranscriptionTask(long messageID, String fileName) {
			super();
			this.messageID = messageID;
			this.fileName = fileName;
		}

		@Override
		protected Integer doInBackground(Void... arg0) {
			int result_code = requestTranscription(messageID, fileName);
			return result_code;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if(result != HttpURLConnection.HTTP_OK){
	    		Logger.d(TAG,	"WatsonHandler.RunWatsonTranscriptionTask.onPostExecute HTTP request failed messageID - "	+ messageID+ " status_code = "+result);
	        	instance.onHttpError(result, messageID, fileName);
			}
		}

	  }
	  
	  public void callWatsonTranscriptionTask(long messageID, String fileName){
  		Logger.d(TAG,	"WatsonHandler.callWatsonTranscriptionTask messageID - "	+ messageID+ " fileName = "+fileName);
		  new RunWatsonTranscriptionTask(messageID, fileName).execute();
	  }
}
