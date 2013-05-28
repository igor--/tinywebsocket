package com.websocket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 
 It's rewritten code of Eric Butler Android Web Socket <eric@codebutler.com>
 * 
 * - removed Android API (thread) - removed double arithmetic usage from Web
 * Socket header parser - added naive Web Server Socket
 * 
 * 
 * The MIT Licence
 * 
 * 
 * Copyright (c) 2009-2012 James Coglan Copyright (c) 2012 Eric Butler Copyright
 * (c) 2013 Igor Kolosov
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the 'Software'), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class WebSocket {
	// private static final String TAG = "WebSocketClient";
	enum FragmentMode {
		TEXT, BINARY
	};

	private URI uri;
	private WebSocketListener listener;
	private Socket socket;
	private List<Http.Header> extraHeaders;
	private ExecutorService executors;
	private static TrustManager[] trustManagers;
	private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	private FragmentMode mode = null;
	private boolean closed;
	private WebSocket ws;
	private Object userData;
	private boolean isClient;
	private String host; // set in server websocket to check in onConnect();
	private String origin; // set in server websocket to check in onConnect();

	public String getHost() {
		return host;
	}

	public String getOrigin() {
		return origin;
	}

	public URI getURI() {
		return uri;
	}

	public void setUserData(Object userData) {
		this.userData = userData;
	}

	public Object getUserData() {
		return userData;
	}

	public boolean isClient() {
		return isClient;
	}

	private void resetBuffer() {
		mode = null;
		buffer.reset();
	}

	public static void setTrustManagers(TrustManager[] tm) {
		trustManagers = tm;
	}

	public WebSocketListener getListener() {
		return listener;
	}

	private WebSocket(boolean isClient) {
		this.isClient = isClient;
		this.ws = this;
	}

	public static WebSocket createClientWebSocket(URI uri, WebSocketListener listener, List<Http.Header> extraHeaders) {
		WebSocket ws = new WebSocket(true);
		ws.uri = uri;
		ws.listener = listener;
		ws.extraHeaders = extraHeaders;
		ws.executors = Executors.newFixedThreadPool(2);
		return ws;
	}

	public static WebSocket createServerWebSocket(Socket socket, WebSocketListener listener) {
		WebSocket ws = new WebSocket(false);
		ws.listener = listener;
		ws.executors = Executors.newFixedThreadPool(2);
		ws.socket = socket;
		return ws;
	}

	private void processIncomingFrame(FrameParser.Frame frame) throws IOException {
		switch (frame.opCode) {
		case FrameParser.OP_CONTINUATION:
			if (mode == null)
				throw new IOException("Unexpected CONTINUATION frame");
			buffer.write(frame.payload);
			if (frame.isFinal) {
				byte[] message = buffer.toByteArray();
				if (mode == FragmentMode.TEXT) {
					listener.onMessage(this, bytesToString(message));
				} else {
					listener.onMessage(this, message);
				}
				resetBuffer();
			}
			break;

		case FrameParser.OP_TEXT:
			if (frame.isFinal) {
				String messageText = bytesToString(frame.payload);
				listener.onMessage(this, messageText);
			} else {
				if (buffer.size() != 0)
					throw new IOException("no FIN frame");
				mode = FragmentMode.TEXT;
				buffer.write(frame.payload);
			}
			break;

		case FrameParser.OP_BINARY:
			if(frame.isFinal) {
				listener.onMessage(this, frame.payload);
			} else {
				if (buffer.size() != 0)
					throw new IOException("no FIN frame");
				mode = FragmentMode.BINARY;
				buffer.write(frame.payload);
			}
			break;

		case FrameParser.OP_CLOSE:
			int code = 0;
			if(frame.payload.length >= 2)
				code = ((frame.payload[0] << 8) | (frame.payload[1] & 0xFF)) & 0xFFFF;
			String reason = null;
			if(frame.payload.length > 2)
				reason = bytesToString(Arrays.copyOfRange(frame.payload, 2, frame.payload.length));
			listener.onDisconnect(this, code, reason);
			disconnect();
			break;

		case FrameParser.OP_PING:
			if (frame.payload.length > 125) {
				throw new IOException("Ping payload too large");
			}
			byte[] data = FrameParser.buildFrame(frame.payload, FrameParser.OP_PONG, -1, isClient, true);
			sendFrame(data);
			break;

		case FrameParser.OP_PONG:
			listener.onPong(this, frame.payload);
			break;
		}
	}

	public void connect() {
		if (isClient)
			connectClient();
		else
			connectServer();
	}

	public void connectServer() {
		ws.executors.execute(new Runnable() {
			public void run() {
				try {
					DataInputStream stream = new DataInputStream(ws.socket.getInputStream());

					// Read HTTP request line.
					String startLine = ws.readLine(stream);
					if (startLine == null) {
						throw new IllegalStateException("Cannot read HTTP request start line");
					}

					Http.RequestLine requestLine = new Http.RequestLine(startLine);
					ws.uri = new URI(requestLine.getRequestURI()); // can be checked in
																													// onConnect()

					// Read HTTP response headers
					HashMap<String, String> map = new HashMap<String, String>();
					String line;
					while ((line = ws.readLine(stream)) != null && line.length() > 0) {
						Http.Header header = new Http.Header(line);
						map.put(header.getName().toLowerCase(), header.getValue());
					}

					String value = map.get("sec-websocket-version");
					if (!"13".equals(value))
						throw new IOException("wrong Sec-WebSocket-Version");

					String key = map.get("sec-websocket-key");
					if (key == null)
						throw new IOException("missed Sec-WebSocket-Key");
					String accept = createAccept(key);

					String upgrade = map.get("upgrade");
					if (upgrade == null || !upgrade.equalsIgnoreCase("websocket"))
						throw new IOException("wrong Upgrade");

					String connection = map.get("connection");
					if (connection == null || !connection.equalsIgnoreCase("upgrade"))
						throw new IOException("wrong Connection");

					// Host and Origin can be checked later in onConnect() callback.
					ws.host = map.get("host");
					if (ws.host == null)
						throw new IOException("Missed 'Host' header");

					ws.origin = map.get("origin");
					if (ws.origin == null)
						throw new IOException("Missed 'Origin' header");

					// Some naive protocol selection.
					String protocols = map.get("sec-websocket-protocol");
					String selectedProtocol = null;
					if (protocols != null && protocols.contains("chat"))
						selectedProtocol = "chat";

					PrintWriter out = new PrintWriter(ws.socket.getOutputStream());
					out.print("HTTP/1.1 101 Switching Protocols\r\n");
					out.print("Upgrade: websocket\r\n");
					out.print("Connection: Upgrade\r\n");
					out.print("Sec-WebSocket-Accept: " + accept + "\r\n");
					if (selectedProtocol != null)
						out.print("Sec-WebSocket-Protocol: " + selectedProtocol + "\r\n");
					out.print("\r\n");
					out.flush();

					ws.listener.onConnect(ws);

					// Read & process frame
					for (;;) {
						FrameParser.Frame frame = FrameParser.readFrame(stream);
						ws.processIncomingFrame(frame);
					}
				} catch (IOException e) {
					ws.listener.onDisconnect(ws, 0, "EOF");
				} catch (Throwable ex) {
					ws.listener.onError(ws, ex);
				} finally {
					ws.disconnect();
				}
			}
		});
	}

	private void connectClient() {
		if (socket != null)
			throw new IllegalStateException("connect() is already called");

		executors.execute(new Runnable() {
			public void run() {
				try {
					int port = (uri.getPort() != -1) ? uri.getPort() : (uri.getScheme().equals("wss") ? 443 : 80);
					String path = (uri.getPath() != null) ? uri.getPath() : "/";
					if (uri.getQuery() != null) {
						path += "?" + uri.getQuery();
					}
					String originScheme = uri.getScheme().equals("wss") ? "https" : "http";
					URI origin = new URI(originScheme, "//" + uri.getHost(), null);
					// To fix: get Origin from extraHeaders if set there.

					SocketFactory factory = uri.getScheme().equals("wss") ? getSSLSocketFactory() : SocketFactory.getDefault();
					socket = factory.createSocket(uri.getHost(), port);

					String key = createKey();
					PrintWriter out = new PrintWriter(socket.getOutputStream());
					out.print("GET " + path + " HTTP/1.1\r\n");
					out.print("Upgrade: websocket\r\n");
					out.print("Connection: Upgrade\r\n");
					out.print("Host: " + uri.getHost() + "\r\n");
					out.print("Origin: " + origin.toString() + "\r\n");
					out.print("Sec-WebSocket-Key: " + key + "\r\n");
					out.print("Sec-WebSocket-Version: 13\r\n");
					if (extraHeaders != null) {
						for (Http.Header header : extraHeaders) {
							out.print(header.toString() + "\r\n");
						}
					}
					out.print("\r\n");
					out.flush();

					DataInputStream stream = new DataInputStream(socket.getInputStream());

					// Read HTTP response status line.
					String startLine = readLine(stream);
					if (startLine == null) {
						throw new IllegalStateException("Received no reply from server.");
					}
					Http.StatusLine statusLine = new Http.StatusLine(startLine);
					int statusCode = statusLine.getStatusCode();
					if (statusCode != 101) {
						throw new IllegalStateException("wrong HTTP response code: " + statusCode);
					}

					// Read HTTP response headers.
					String line;
					while ((line = readLine(stream)) != null && line.length() > 0) {
						Http.Header header = new Http.Header(line);
						if (header.getName().equalsIgnoreCase("Sec-WebSocket-Accept")) {
							String receivedAccept = header.getValue();
							String shouldBeAccept = createAccept(key);
							if (!receivedAccept.equals(shouldBeAccept))
								throw new IllegalStateException("Wrong Sec-WebSocket-Accept: " + receivedAccept + " should be: " + shouldBeAccept);
						}
					}

					listener.onConnect(ws);

					// Read & process frame
					for (;;) {
						FrameParser.Frame frame = FrameParser.readFrame(stream);
						processIncomingFrame(frame);
					}
				} catch (IOException e) {
					// System.out.println("IOException " + e);
					listener.onDisconnect(ws, 0, "EOF");
				} catch (Throwable ex) {
					listener.onError(ws, ex);
				} finally {
					disconnect();
				}
			}
		});
	}

	private void disconnect() {
		if (socket != null) {
			executors.execute(new Runnable() {
				@Override
				public void run() {
					try {
						socket.close();
						socket = null;
						executors.shutdown();
						closed = true;
					} catch (IOException ex) {
						// listener.onError(ws, ex);
					}
				}
			});
		}
	}

	public void send(String str) {
		sendFragment(str, true, true);
	}

	public void send(byte[] data) {
		sendFragment(data, true, true);
	}

	public void sendFragment(String str, boolean isFirst, boolean isLast) {
		if (closed)
			return;
		byte[] data = stringToBytes(str);
		byte[] frame = FrameParser.buildFrame(data, isFirst ? FrameParser.OP_TEXT : FrameParser.OP_CONTINUATION, -1, isClient, isLast);
		sendFrame(frame);
	}

	public void sendFragment(byte[] data, boolean isFirst, boolean isLast) {
		if (closed)
			return;
		byte[] frame = FrameParser.buildFrame(data, isFirst ? FrameParser.OP_BINARY : FrameParser.OP_CONTINUATION, -1, isClient, isLast);
		sendFrame(frame);
	}

	public void ping(byte[] data) {
		if (closed)
			return;
		byte[] frame = FrameParser.buildFrame(data, FrameParser.OP_PING, -1, isClient, true);
		sendFrame(frame);
	}

	public void close(int code, String reason) {
		if (closed)
			return;
		byte[] data;
		if (reason != null && reason.length() > 0) {
			try {
				data = reason.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		} else {
			data = new byte[0];
		}
		byte[] frame = FrameParser.buildFrame(data, FrameParser.OP_CLOSE, code, isClient, true);
		sendFrame(frame);
		disconnect();
	}

	private synchronized void sendFrame(final byte[] frame) {
		executors.execute(new Runnable() {
			@Override
			public void run() {
				try {
					OutputStream outputStream = socket.getOutputStream();
					outputStream.write(frame);
					outputStream.flush();
				} catch (IOException e) {
					listener.onError(ws, e);
				}
			}
		});
	}

	private String readLine(DataInputStream reader) throws IOException {
		int readChar = reader.read();
		if (readChar == -1) {
			return null;
		}
		StringBuilder string = new StringBuilder("");
		while (readChar != '\n') {
			if (readChar != '\r') {
				string.append((char) readChar);
			}

			readChar = reader.read();
			if (readChar == -1) {
				return null;
			}
		}
		return string.toString();
	}

	private SSLSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, trustManagers, null);
		return context.getSocketFactory();
	}

	public static String bytesToString(byte[] buffer) {
		try {
			return new String(buffer, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] stringToBytes(String str) {
		try {
			return str.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String createKey() throws IOException {
		byte[] nonce = new byte[16];
		FrameParser.random.nextBytes(nonce);
		return new String(Base64Encoder.doEncode(nonce));
	}

	public static String createAccept(String key) throws IOException {
		String str = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
		byte inData[] = null;
		try {
			inData = str.getBytes("ASCII");
		} catch (UnsupportedEncodingException e1) {
			throw new IllegalStateException("ASCII encoding is not supported");
		}
		byte digestData[];
		try {
			digestData = MessageDigest.getInstance("SHA1").digest(inData);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 is not supported");
		}
		return new String(Base64Encoder.encode(digestData));
	}
}
