package com.att.mobile.android.infra.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

/**
 * Usage:
 * <pre>
 * String crypto = SimpleCrypto.encrypt(masterpassword, cleartext)
 * ...
 * String cleartext = SimpleCrypto.decrypt(masterpassword, crypto)
 * </pre>
 *
 *
 *    Make sure you do NOT use a static string for the key. 
 *    Instead, use some permutation of the SIM card serial or IMSI
 *    
 */
public class Crypto {
	
	public static final String PROVIDER_CRYPTO = "Crypto";
	
	/**
	 * Encrypts a given text using the default encryption provider and a given
	 * seed<br>
	 * <br>
	 * <b>Note:</b> This method is deprecated since google changed the default
	 * encryption provider. This means that any encryption you've made using the
	 * default provider prior to 4.2 will not decrypt in 4.2 and above. For back
	 * support use {@link #encrypt(String, String, String)} with
	 * {@link #PROVIDER_CRYPTO}
	 * 
	 * @param seed
	 * @param cleartext
	 * @return
	 * @throws Exception
	 */
	@Deprecated
	public static String encrypt(String seed, String cleartext) throws Exception {
		return encrypt(seed, cleartext, null); 
	}
	
	/**
	 * Encrypts a given text using a given encryption provider and a given seed
	 * 
	 * @param seed
	 * @param cleartext
	 * @param provider
	 *            either a specific supported provider or null for default
	 * @return
	 * @throws Exception
	 */
	public static String encrypt(String seed, String cleartext, String provider) throws Exception {
		byte[] rawKey = getRawKey(seed.getBytes(), provider);
		byte[] result = encrypt(rawKey, cleartext.getBytes());
		return toHex(result);
	}

	/**
	 * Decrypts an encrypted text using the default provider and a given seed.<br>
	 * <br>
	 * <b>Note:</b> This method is deprecated since google changed the default
	 * encryption provider. This means that any encryption you've made using the
	 * default provider prior to 4.2 will not decrypt in 4.2 and above. For back
	 * support use {@link #decrypt(String, String, String)} with
	 * {@link #PROVIDER_CRYPTO}
	 * 
	 * @param seed
	 * @param encrypted
	 * @return
	 * @throws Exception
	 */
	@Deprecated
	public static String decrypt(String seed, String encrypted) throws Exception {
		return decrypt(seed, encrypted, null);
	}
	
	/**
	 * Decrypts an encrypted text using a given provider and a given seed.<br>
	 * 
	 * @param seed
	 * @param encrypted
	 * @param provider
	 *            either a specific supported provider or null for default
	 * @return
	 * @throws Exception
	 */
	public static String decrypt(String seed, String encrypted, String provider) throws Exception {
		byte[] rawKey = getRawKey(seed.getBytes(), provider);
		byte[] enc = toByte(encrypted);
		byte[] result = decrypt(rawKey, enc);
		return new String(result);
	}

	private static byte[] getRawKey(byte[] seed, String provider) throws Exception {
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		SecureRandom sr = provider != null ? SecureRandom.getInstance(
				"SHA1PRNG", provider) : SecureRandom.getInstance("SHA1PRNG");
		sr.setSeed(seed);
		kgen.init(128, sr); // 192 and 256 bits may not be available
		SecretKey skey = kgen.generateKey();
		byte[] raw = skey.getEncoded();
		return raw;
	}


	private static byte[] encrypt(byte[] raw, byte[] clear) throws Exception {
		SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		byte[] encrypted = cipher.doFinal(clear);
		return encrypted;
	}

	private static byte[] decrypt(byte[] raw, byte[] encrypted) throws Exception {
		SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		byte[] decrypted = cipher.doFinal(encrypted);
		return decrypted;
	}

	public static String toHex(String txt) {
		return toHex(txt.getBytes());
	}
	public static String fromHex(String hex) {
		return new String(toByte(hex));
	}

	public static byte[] toByte(String hexString) {
		int len = hexString.length()/2;
		byte[] result = new byte[len];
		for (int i = 0; i < len; i++)
			result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
		return result;
	}

	public static String toHex(byte[] buf) {
		if (buf == null)
			return "";
		StringBuffer result = new StringBuffer(2*buf.length);
		for (int i = 0; i < buf.length; i++) {
			appendHex(result, buf[i]);
		}
		return result.toString();
	}
	private final static String HEX = "0123456789ABCDEF";
	private static void appendHex(StringBuffer sb, byte b) {
		sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f));
	}

	public static class IMAP4{
		
		/*
		 * vip2.acds.attwireless.net:143?f=0&v=601&m=2128220314&P=XldQVw==&S=C&s=993&d=vip2.acds.attwireless.net:587&t=4:2128220314:A:redwaap035:ms04:IMAP4MWI_STT:47583
		 * String password = "XldQVw=="
		 */
		
		/**
		 * @param base64Password the password received in the SMS encoded and base 64
		 * @param tn the telephone number received in the SMS
		 */
		public static String decrypt(String base64Password, String tn)
		{
			final String key = "luckytechnologycompany";
			char[] tmpKey = tn.toCharArray();
			
			for(int i = 0; (i < tmpKey.length) && (i < key.length()); i++)
			{
				if (tmpKey[i] != key.charAt(i))
				{
					tmpKey[i] = (char)(tmpKey[i] ^ key.charAt(i));
					tmpKey[i]  |= 0x60;
					tmpKey[i] &= 0x6f;
				}
			}

			//String passwd = new String(Base64.decode(base64Password, Base64.DEFAULT));
			//char[] output = passwd.toCharArray();
			
			byte[] passwdBA = Base64.decode(base64Password, Base64.DEFAULT);
			ByteBuffer passwBB = ByteBuffer.wrap(passwdBA);
			CharBuffer passwCB = Charset.forName(Charset.defaultCharset().name()).decode(passwBB);
			char[] output = new char[passwCB.limit()];
			passwCB.get(output);
			
			for(int i = 0; (i < output.length) && (i < tmpKey.length); i++)
			{
				if (output[i] != tmpKey[i]){
					output[i] = (char)(output[i] ^ tmpKey[i]);
				}
			}

			return new String(output);
		}
	}
}