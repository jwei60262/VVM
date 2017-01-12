package com.att.mobile.android.vvm.model;

/**
 * Caches data about a single contact from the contacts database
 */
public class Contact
{
	/** the contact ID from the contacts database, or -1 if this is
	 * an unknown contact
	 */
	public long contactId;

	/**
	 * the contact display name if known, or a formatted phone number
	 * for unknown contacts
	 */
	public String displayName;

	/**
	 * contact first name, calculated from display name, may be null
	 */
	public String firstName;

	/**
	 * contact middle name, calculated from display name, may be null
	 */
	public String middleName;

	/**
	 * contact last name, calculated from display name, may be null
	 */
	public String lastName;

	/*
	 * type of the phone number, i.e. mobile, work, etc.
	 */
	public int phoneType;
	
	/*
	 * type of latest EXCHANGE type: MMS/SMS=1 CALL=2 email/other=3.
	 * value define in: UMessage.Exchange.TYPE_...
	 */
	
	public int exchangeType;

	/*
	 * Phone number associated with this/these contact(s)
	 */
	public String phoneNumber;

	/*
	 * Email address this/these contact(s)
	 */
	public String email;
	
	/*
	 * label of the email/phoneNumber - to be used in case the type = 0 (custom type)
	 */
	public String label;
	
	/*
	 * Indicate if the contact has more than one address type
	 */
	public boolean hasManyTypes;

	/*
	 * pointer to the next contact in list of the contacts with the same
	 * phone number
	 */
	public Contact next = null;
	
	
	

	// Copy an exiting contact recursively into a new structure and linked list
	public static Contact copy(Contact c) {
		Contact tmp = new Contact();
		Contact tmpCopy = tmp;  // save starting point
		copyFields(c, tmp);
		
		while (c.next != null) {
			tmp.next = new Contact();
			tmp = tmp.next;
			c = c.next;
			copyFields(c, tmp);
		}
		
		tmp = tmpCopy;
		return tmp;
		
	}
	
	// Copy immutable fields of the Contact 
	private static void copyFields(Contact src, Contact dest) {
		dest.contactId = src.contactId;
		dest.displayName = src.displayName;
		dest.firstName = src.firstName;
		dest.middleName = src.middleName;
		dest.lastName = src.lastName;
		dest.phoneType = src.phoneType;
		dest.exchangeType = src.exchangeType;
		dest.phoneNumber = src.phoneNumber;
		dest.email = src.email;
		dest.label = src.label;
		dest.hasManyTypes = src.hasManyTypes;
		dest.next = null; 
	}
}
