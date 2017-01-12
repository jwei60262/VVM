package com.att.mobile.android.infra.network;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


public class TLSSocketFactory extends SSLSocketFactory
{

    private static final boolean USE_TLS_1_2 = false;
    private static final String TAG = TLSSocketFactory.class.getSimpleName();
    private SSLSocketFactory internalSSLSocketFactory;

    public TLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException
    {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        internalSSLSocketFactory = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites()
    {
        return internalSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites()
    {
        return internalSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException
    {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException
    {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException
    {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException
    {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException
    {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTLSOnSocket(Socket socket)
    {
        if (socket != null && (socket instanceof SSLSocket) && USE_TLS_1_2)
        {
            ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2"});
            Log.d(TAG, "enableTLSOnSocket: TLS version 1.2 enabled");
        }
        return socket;
    }

    /*private static class AdditionalKeyStoresTrustManager implements X509TrustManager
    {
        private static final String TAG = AdditionalKeyStoresTrustManager.class.getSimpleName();

        protected ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();

        protected AdditionalKeyStoresTrustManager(KeyStore... additionalKeyStores)
        {
            final ArrayList<TrustManagerFactory> factories = new ArrayList<TrustManagerFactory>();
            try
            {
                // The default Trust manager with default keystore
                final TrustManagerFactory original = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                original.init((KeyStore) null);
                factories.add(original);
                for (KeyStore keyStore : additionalKeyStores)
                {
                    final TrustManagerFactory additionalCerts = TrustManagerFactory
                            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    additionalCerts.init(keyStore);
                    factories.add(additionalCerts);
                }

            }
            catch (Exception e)
            {
                Log.e(TAG, "ctor", e);
                throw new RuntimeException(e);
            }


            for (TrustManagerFactory tmf : factories)
            {
                for (TrustManager tm : tmf.getTrustManagers())
                {
                    if (tm instanceof X509TrustManager)
                    {
                        x509TrustManagers.add((X509TrustManager) tm);
                    }
                    if (x509TrustManagers.size() == 0)
                    {
                        throw new RuntimeException("Couldn't find any X509TrustManagers");
                    }
                }
            }
        }


        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
        {
            final X509TrustManager defaultX509TrustManager = x509TrustManagers.get(0);
            defaultX509TrustManager.checkClientTrusted(chain, authType);
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
        {
            X509Certificate[] chainOne = null;
            for (X509TrustManager tm : x509TrustManagers)
            {

                if (chain != null)
                {
                    chainOne = new X509Certificate[1];
                    for (X509Certificate aChain : chain)
                    {
                        chainOne[0] = aChain;
                        try
                        {
                            tm.checkServerTrusted(chainOne, authType);
                            return;
                        }
                        catch (CertificateException e)
                        {
                            Log.e(TAG, "Catched CertificateException", e);
                        }
                    }
                }
            }
            throw new CertificateException();
        }

        public X509Certificate[] getAcceptedIssuers()
        {
            final ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
            for (X509TrustManager tm : x509TrustManagers)
                list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
            return list.toArray(new X509Certificate[list.size()]);
        }
    }*/
}
