package com.att.mobile.android.vvm.model.greeting;

import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;

public class GreetingFactory {

	private static final String TAG = "GreetingFactory";
	/**
	 * Supported greeting types
	 * 
	 * @author mkoltnuk
	 */
	public enum SUPPORTED_GREETING_TYPES {
		Personal, StandardWithName, StandardWithNumber
	}
	public static String PERSONAL = "Personal";
	public static String RECORD_NAME = "RecordedName";
	public static String CUSTOM = "Custom";
	public static String NAME = "Name";


	/**
	 * Creates greetings list out of available/allowed/changeable server strings
	 * 
	 * @param allowedGreetings
	 * @param changeableGreetings
	 * @param selectedGreeting
	 * @param maxAllowedRecording
	 * @return
	 * @throws NoSuchProviderException
	 */
	public static ArrayList<Greeting> createGreetings(Context context, String allowedGreetings, String changeableGreetings, String selectedGreeting,
			String maxCustomRecording, String maxRecordedName) {
		ArrayList<Greeting> retList = new ArrayList<Greeting>();

		Logger.d(TAG, "GreetingFactory::createGreetings - Allowed Greetings => " + allowedGreetings);
		Logger.d(TAG, "GreetingFactory::createGreetings - Changeable Greetings => " + changeableGreetings);
		Logger.d(TAG, "GreetingFactory::createGreetings - Selected Greeting => " + selectedGreeting);
		Logger.d(TAG, "GreetingFactory::createGreetings - Max Custom Record Time => " + maxCustomRecording);
		Logger.d(TAG, "GreetingFactory::createGreetings - Max Name Record Time => " + maxRecordedName);

		if (allowedGreetings != null) {
			StringTokenizer st = new StringTokenizer(allowedGreetings, ",");
			selectedGreeting = selectedGreeting.toLowerCase();

			String tmpVal;
			while (st.hasMoreElements()) {
				tmpVal = st.nextToken().toLowerCase();
				try {
					SUPPORTED_GREETING_TYPES type = convertSimpleTypeToOriginalType(tmpVal);
					Greeting g = null;
					if (type == SUPPORTED_GREETING_TYPES.StandardWithName){
						g = createGreeting(context, type, true, selectedGreeting.equals(tmpVal),
								Integer.parseInt(maxRecordedName.trim()));
					}else{
						g = createGreeting(context, type, isGreetingChangeable(tmpVal, changeableGreetings), selectedGreeting.equals(tmpVal),
								Integer.parseInt(maxCustomRecording.trim()));
					}

					if (g != null) {
						Logger.d(TAG, "Greeting of type " + tmpVal + " was added to list.");
						retList.add(g);
					}
				} catch (Exception e) {
					// Nothing to do, type not supported and won't be shown
					Log.e(TAG, "Unsupported greeting type: " + tmpVal, e);
				}
			}
		}

		return retList;
	}

	// Creates the requested greeting type
	private static Greeting createGreeting(Context context, SUPPORTED_GREETING_TYPES originalType, Boolean isChangeable, Boolean isSelected,
			int maxRecordTime) {
		Greeting retVal = null;

		switch (originalType) {
		case Personal:
			retVal = new PersonalGreeting(context, isChangeable, isSelected, maxRecordTime);
			break;
		case StandardWithName:
			retVal = new StandartWithNameGreeting(context, isSelected, maxRecordTime);
			break;
		case StandardWithNumber:
			retVal = new StandartWithNumberGreeting(context, isChangeable, isSelected, maxRecordTime);
			break;
		default: 
			break;
		}
		return retVal;
	}

	/**
	 * Check if a greeting is changeable
	 * 
	 * @param greetingSimple
	 *            simple greeting name
	 * @param changeableGreetings
	 *            available changeable greeting
	 * @return if the specific greeting is changeable
	 */
	private static Boolean isGreetingChangeable(String greetingSimple, String changeableGreetings) {
		List<String> changeableGreetingsList = new ArrayList<String>();
		Boolean isChangeable = false;

		if (greetingSimple != null && changeableGreetings != null) {
			StringTokenizer st = new StringTokenizer(changeableGreetings, ",");
			while (st.hasMoreElements()) {
				changeableGreetingsList.add(st.nextToken()); // fill changeable
				// greeting
				// names list
			}

			// Check if greeting name exists in changeable greetings names
			for (String cGreeting : changeableGreetingsList) {
				if (cGreeting.toLowerCase().equals(greetingSimple.toLowerCase())) { // name
					// found
					isChangeable = true;
					break;
				}
			}
		} else {
			throw new NullPointerException("Greeting type cannot be null. You must specify a valid value of greeting types.");
		}

		return isChangeable;
	}

	/**
	 * Gets the UI name of the greeting returned from the server.
	 * 
	 * @param originalType
	 *            such as Personal,StandardWithName,StandardWithNumber
	 * @return greeting UI name such as Default,Name,Personal(Custom)
	 * 
	 * @throws NoSuchProviderException
	 */
	public static String convertOriginalTypeToSimpleType(SUPPORTED_GREETING_TYPES originalType) {
		switch (originalType) {
		case StandardWithNumber:
			return "Default";
		case StandardWithName:
			return "Name";
		case Personal:
			return "Custom";
		default: // greeting type not supported
			Logger.d(TAG, "The following greeting type: " + originalType.toString() + " is not supported.");
			return null;
		}
	}

	public static SUPPORTED_GREETING_TYPES convertSimpleTypeToOriginalType(String originalType) {
		if (originalType.toLowerCase().equals(SUPPORTED_GREETING_TYPES.Personal.toString().toLowerCase())) {
			return SUPPORTED_GREETING_TYPES.Personal;
		} else if (originalType.toLowerCase().equals(SUPPORTED_GREETING_TYPES.StandardWithName.toString().toLowerCase())) {
			return SUPPORTED_GREETING_TYPES.StandardWithName;
		} else if (originalType.toLowerCase().equals(SUPPORTED_GREETING_TYPES.StandardWithNumber.toString().toLowerCase())) {
			return SUPPORTED_GREETING_TYPES.StandardWithNumber;
		} else {
			Logger.d(TAG, "The following greeting type: " + originalType.toString() + " is not supported.");
			return null;
		}
	}
}
