
package com.att.mobile.android.infra.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;

import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;

public class ExtendedSSLSocketFactory extends SSLSocketFactory {

	private static ExtendedSSLSocketFactory INSTANCE;
	private static final String TAG = "ExtendedSSLSocketFactory";
	

	protected SSLContext sslContext = SSLContext.getInstance("TLS");

	public static ExtendedSSLSocketFactory createInstance(InputStream rawKeystoreInputStream, char[] password, X509HostnameVerifier hostnameVerifier) {
		if (INSTANCE == null) {
			try {
				Logger.d(TAG, "going to create ExtendedSSLSocketFactory instance with X509HostnameVerifier = " + hostnameVerifier.toString());
				INSTANCE = new ExtendedSSLSocketFactory(rawKeystoreInputStream, password, hostnameVerifier);
			} catch (Exception e) {
				Log.e(TAG, "Error creating instance for EncoreSSLSocketFactory", e);
			}
		}
		return INSTANCE;
	}

	public static ExtendedSSLSocketFactory getInstance() throws CertificateException {
		if (INSTANCE == null) {
			throw new CertificateException("you must first create an instance with a valid Keystore and password.");
		}
		return INSTANCE;
	}

	/**
	 * adds the appropriate certificate to the https request
	 * 
	 * @param in - the raw resource, which contains the keystore with your trusted certificates (root and any
	 *            intermediate certs) InputStream in =
	 *            EncoreApplication.getContext().getResources().openRawResource(R.raw.attmessages);
	 *            //R.raw.encorekeystore);
	 * @param rawKeystoreInputStream
	 * @param password
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 * @throws KeyStoreException
	 * @throws UnrecoverableKeyException
	 * @throws CertificateException
	 * @throws IOException
	 */
	private ExtendedSSLSocketFactory(InputStream rawKeystoreInputStream, char[] password, X509HostnameVerifier hostnameVerifier)
				throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException,
			CertificateException, IOException {

		super(null, null, null, null, null, null);
		// Get an instance of the Bouncy Castle KeyStore format
		KeyStore trusted = KeyStore.getInstance("BKS");

		// Initialize the keystore with the provided trusted
		// certificates
		// Also provide the password of the keystore
		trusted.load(rawKeystoreInputStream, password);
		rawKeystoreInputStream.close();
		// Pass the keystore to the SSLSocketFactory. The factory is responsible
		// for the verification of the server certificate.
		// Hostname verification from certificate
		setHostnameVerifier(hostnameVerifier);
		
		sslContext.init(null, new TrustManager[] {
					new AdditionalKeyStoresTrustManager(trusted)
			}, null);
	}

	@Override
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
		return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
	}

	@Override
	public Socket createSocket() throws IOException {
		return sslContext.getSocketFactory().createSocket();
	}

	/**
	 * Based on http://download.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html#X509TrustManager
	 */
	private static class AdditionalKeyStoresTrustManager implements X509TrustManager {

		protected ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();

		protected AdditionalKeyStoresTrustManager(KeyStore... additionalkeyStores) {
			final ArrayList<TrustManagerFactory> factories = new ArrayList<TrustManagerFactory>();
			try {
				// The default Trustmanager with default keystore
				final TrustManagerFactory original = TrustManagerFactory.getInstance(TrustManagerFactory
							.getDefaultAlgorithm());
				original.init((KeyStore) null);
				factories.add(original);
				for (KeyStore keyStore : additionalkeyStores) {
					final TrustManagerFactory additionalCerts = TrustManagerFactory.getInstance(TrustManagerFactory
								.getDefaultAlgorithm());
					additionalCerts.init(keyStore);
					// printKeyStoreAliasAndCerts(keyStore);
					factories.add(additionalCerts);
				}

				// LogWrapper.v("AdditionalKeyStoresTrustManager", "TrustManagerFactory factories = " + factories.size());
			} catch (Exception e) {
				Log.e(TAG, "ctor", e);
				throw new RuntimeException(e);
			}
			/*
			 * Iterate over the returned trustmanagers, and hold on to any that are X509TrustManagers
			 */
			for (TrustManagerFactory tmf : factories) {
				for (TrustManager tm : tmf.getTrustManagers()) {
					if (tm instanceof X509TrustManager) {
						x509TrustManagers.add((X509TrustManager) tm);
					}
					if (x509TrustManagers.size() == 0) {
						throw new RuntimeException("Couldn't find any X509TrustManagers");
					}
				}
			}
		}

		/*
		 * Delegate to the default trust manager.
		 */
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			final X509TrustManager defaultX509TrustManager = x509TrustManagers.get(0);
			defaultX509TrustManager.checkClientTrusted(chain, authType);
		}

		/*
		 * Loop over the trustmanagers until we find one that accepts our server
		 */
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			X509Certificate[] chainOne = null;
			for (X509TrustManager tm : x509TrustManagers) {
				if (chain != null) {
					chainOne = new X509Certificate[1];
					for (int i = 0; i < chain.length; i++) {
						chainOne[0] = chain[i];
						try {
							tm.checkServerTrusted(chainOne, authType);
							return;
						} catch (CertificateException e) {
							Log.e(TAG, "EncoreSSLSocketFactory CertificateException ", e);
						}
					}
				}
			}
			throw new CertificateException();
		}

		public X509Certificate[] getAcceptedIssuers() {
			final ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
			for (X509TrustManager tm : x509TrustManagers)
				list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
			return list.toArray(new X509Certificate[list.size()]);
		}
	}
}
