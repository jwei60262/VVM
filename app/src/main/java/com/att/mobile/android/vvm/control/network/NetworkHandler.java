
package com.att.mobile.android.vvm.control.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

import org.apache.http.conn.ssl.SSLSocketFactory;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.network.ExtendedSSLSocketFactory;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;

/**
 * Handle the TCP connection to the IMAP4 server removed support for SSL - 26.62011 - lior david
 * 
 * @author ldavid
 */
public class NetworkHandler {

	private Socket socket;
	private SSLSocket secureSocket;

	private boolean connected = false;

	private InputStream input = null;
	private OutputStream output = null;
	private byte[] readBuffer = new byte[1024];
	// 300 KB - to include most of the files in the first chunk, files duration
	// is limited to 2 minutes AMR
	private static final int CHUNK_SIZE = 307200;
	private static byte[] fileReadBuffer = new byte[CHUNK_SIZE];
	private static final String TAG = "NetworkHandler";

	public NetworkHandler() {
	}

	    
	/**
	 * This method opens a TCP connection on the specified host. The Host String must contain also a port number e.g.
	 * 10.0.0.1:500
	 * 
	 * @return true if connection was open successfully. false otherwise
	 */
	public boolean connect(String host, int port, int timeout) {

		if (connected)
			return true;

		try {

			// opens a read/write, connection on the specified host.
			Logger.d(TAG, "NetworkHandler.connect() connecting to "
					+ host + ":" + port +", timeout = " + timeout + " seconds");
			
			// Open a socket without any parameters. It hasn�t been binded or connected
			socket = new Socket();

			// Bind to a local ephemeral port
			socket.bind(null);

			// Connect to google.com on port 80 with a timeout of 500 milliseconds
			// set default for the timeout
			if (timeout <= 0) {
				timeout = 60;
			}

			socket.connect(new InetSocketAddress(host, port), timeout * 1000);

			// socketConnection = new Socket(host, port);
			input = socket.getInputStream();
			output = socket.getOutputStream();
			connected = true;
			return true;
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			close();
		}
		return false;
	}

	/**
	 * This method opens a TCP connection on the specified host. The Host String must contain also a port number e.g.
	 * 10.0.0.1:500
	 * 
	 * @return true if connection was open successfully. false otherwise
	 */
	public boolean connectSSL(Context context, String host, int port, int timeout) {

		if (connected)
			return true;

		try {
			// opens a read/write, connection on the specified host.
			Logger.d(TAG, "NetworkHandler.connectSSL() connecting to "
					+ host + ":" + port + ", timeout = " + timeout + " seconds");

			// Open a socket without any parameters. It hasn't been binded or connected
			// the raw resource and password provides a keystore with trusted certificate
			Logger.d(TAG, "NetworkHandler.connectSSL() going to create secure socket with STRICT_HOSTNAME_VERIFIER");
			
			secureSocket = (SSLSocket) ExtendedSSLSocketFactory.createInstance(
					context.getResources().openRawResource(R.raw.attmessages), "encore".toCharArray(),SSLSocketFactory.STRICT_HOSTNAME_VERIFIER).createSocket();

			if (secureSocket != null) {
				Logger.d(TAG, "NetworkHandler.connectSSL() socket created");

				// Bind to a local ephemeral port
				secureSocket.bind(null);

				// Connect to google.com on port 80 with a timeout of 500 milliseconds
				// set default for the timeout
				if (timeout <= 0) {
					timeout = 60;
				}
				secureSocket.connect(new InetSocketAddress(host, port), timeout * 1000);

				input = secureSocket.getInputStream();
				output = secureSocket.getOutputStream();
				connected = true;
				return true;
			} else {
				Log.e(TAG, "NetworkHandler.connectSSL() failed");
				close();
			}

		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			close();
		}
		return false;
	}

	/**
	 * closes the OutputStream, InputStream and Socket.
	 */
	public void close() {

		if (socket != null) {
			try {
				socket.shutdownInput();
				input = null;
				socket.shutdownOutput();
				output = null;

				socket.close();
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
			socket = null;
		}

		try {
			if (secureSocket != null) {
				secureSocket.close();
				secureSocket = null;
			}
			if (socket != null) {
				socket.close();
				socket = null;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		connected = false;
	}

	/**
	 * Sends byte-stream data via the connection against the server.
	 * 
	 * @param data (byte[]) the data to send
	 * @throws IOException in case of an error.
	 */
	public void send(byte[] data) throws IOException, SSLException {
		// in case the connection against the server is available
		if (connected && (socket != null || secureSocket != null) && output != null) {
			// writes the data to the output stream of the connection against
			// the server
			output.write(data);
			output.flush();
			return;
		}

		// in case the connection against the server is NOT available
		close();
		throw new IOException("connection closed");
	}

	/**
	 * Sends byte-stream data via the connection against the server.
	 * 
	 * @param data (byte[]) the data to send
	 * @param offset (int) the start position in buffer from where to get bytes.
	 * @param count (int) the number of bytes from buffer to write to this stream.
	 * @throws IOException in case of an error.
	 */
	public void send(byte[] data, int offset, int count) throws IOException, SSLException {
		// in case the connection against the server is available
		if (connected && (socket != null || secureSocket != null) && output != null) {
			// writes the data to the output stream of the connection against
			// the server
			output.write(data, offset, count);
			output.flush();
			return;
		}

		// in case the connection against the server is NOT available
		close();
		throw new IOException("connection closed");
	}

	/**
	 * Get the next line of data from the connection stream.
	 */
	public byte[] receiveNextData() throws IOException, SSLException {
		return receiveNextData(false);
	}

	/**
	 * Get the next line of data from the connection stream.
	 */
	public byte[] receiveNextData(boolean plusLF) throws IOException, SSLException{
		long waitTime = 60000;
		boolean stop = false;
		int count;

		long start = System.currentTimeMillis();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		/**
		 * The "stop" flag will be set as soon as we have received a complete line, that is, a line terminated by CR/LF.
		 * Until this is the case, the following block is repeated.
		 */
		while (!stop) {
			count = 0;

			/**
			 * The inner block reads single bytes from the InputStream until, again, a line terminator is read or the
			 * buffer is full.
			 */
			while (true) {
				int actual = input.read(readBuffer, count, 1);
				/**
				 * If -1 is returned, the InputStream is already closed, probably because the connection is broken, or
				 * the server didn't like is. Try to close the connection, but ignore any errors that might result from
				 * this.
				 */
				if (actual == -1) {
					try {
						input.close();
					} catch (IOException e) {
						System.out
								.println("IMAP4Protocol.receiveNextData() Exp :"
										+ e.toString());
					}
					close();
					throw new IOException("Connection closed");
				}

				/**
				 * If no bytes have been received, we wait a little while (by yielding processing time to other
				 * threads).
				 */
				else if (actual == 0) {
					Thread.yield();
					if (System.currentTimeMillis() - start > waitTime) {
						close();
						throw new IOException("TimeOut");
					}
				}

				/**
				 * If a byte has been read, examine it and put it in the buffer.
				 */
				else {
					start = System.currentTimeMillis();
					byte b = readBuffer[count];

					/**
					 * Ignore all CRs.
					 */
					if (b == 0x0D) {
						/* Ignore CRs */
					}
					/**
					 * Take LF as line separator.
					 */
					else if (b == 0x0A) {
						// add the LF to the returned string
						if (plusLF) {
							count++;
						}
						stop = true;
						break;
					}
					/**
					 * Everything else makes it into the buffer.
					 */
					else {
						count++;
						if (count == readBuffer.length)
							break;
					}
				}
			}

			baos.write(readBuffer, 0, count);
		}
		return baos.toByteArray();
	}

	/**
	 * reads the next chunk of bytes of a file data from the input stream. the chunk size is limited by the minimum of
	 * the chunk size and the size of the unread part of the file.
	 * 
	 * @return
	 * @throws IOException
	 */
	public byte[] receiveNextChunk(int fileSizeToRead) throws IOException, SSLException {

		int actual = 0, totalRead = 0;
		int maxBytesToRead = Math.min(fileReadBuffer.length, fileSizeToRead);

		while (totalRead < maxBytesToRead) {

			actual = input.read(fileReadBuffer, totalRead, maxBytesToRead
					- totalRead);

			if (actual == -1) {
				try {
					input.close();
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
				}
				close();
				throw new IOException("Connection closed");
			} else {
				totalRead += actual;
			}
		}

		// holds the whole data
		byte[] wholeData = null;

		if (totalRead > 0) {
			wholeData = new byte[totalRead];
			System.arraycopy(fileReadBuffer, 0, wholeData, 0, totalRead);
		}
		return wholeData;
	}

	/**
	 * @return true is the tcp connection is open.
	 */
	public boolean isConnected() {
		return connected;
	}

}
