package com.att.mobile.android.infra.sim;


/**
 * This class is used as a return value by SimManager.validateSim
 * 
 * @see SimManager#validateSim()
 * 
 */
public final class SimValidationResult {
	
	private static final String[] RESULT_TEXT = new String[] {
		"NO_SIM",
		"NOT_READY",
		"SIM_SWAPPED",
		"FIRST_SIM_USE",
		"SIM_VALID",
		"NO_PERMISSION"
	};
	
	/**
	 * There's currently no Sim card in the device
	 */
	public static final int NO_SIM = 0;
	
	/**
	 * The Sim is not ready
	 */
	public static final int NOT_READY = 1;
	
	/**
	 * A sim swap has been detected 
	 */
	public static final int SIM_SWAPPED = 2;
	
	/**
	 * This is the first Sim validation. Should usually be treader as SIM_SWAPPED
	 */
	public static final int FIRST_SIM_USE = 3;
	
	/**
	 * The Sim is valid. This is the same Sim card as detected in the last operation
	 */
	public static final int SIM_VALID = 4;
	
	public static final int NO_PERMISSION = 5;
	
	private int mResult;
	
	/**
	 * Package protected constructor
	 * @param result
	 */
	SimValidationResult(int result) {
		mResult = result;
	}
	
	/**
	 * Returns true if the sim is valid
	 */
	public boolean isSimValid() {
		return (mResult == SIM_VALID);
	}
	
	public boolean isSimPresentAndReady() {
		return (mResult != NO_SIM && mResult != NOT_READY);
	}
	
	public boolean isSimSwapped() {
		return (mResult == SIM_SWAPPED);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null || (!(obj instanceof SimValidationResult))) {
			return false;
		}
	
		return mResult == ((SimValidationResult)obj).mResult;
	}
	
	@Override
	public int hashCode() {
		return mResult;
	}
	
	/**
	 * Returns the ordinal value of the result
	 * 
	 * @see #NO_SIM
	 * @see #NOT_READY
	 * @see #SIM_SWAPPED
	 * @see #FIRST_SIM_USE
	 * @see #SIM_VALID
	 */
	public int ordinal() {
		return mResult;
	}
	
	@Override
	public String toString() {
		return RESULT_TEXT[mResult];
	}
}
