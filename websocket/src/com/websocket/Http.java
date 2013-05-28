package com.websocket;

import java.util.StringTokenizer;

/**
 * Very simple HTTP parser implementation
 */
public class Http {

	// message-header = field-name ":" [ field-value ]
	public static class Header {
		String name;
		String value;

		public Header(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public Header(String line) throws IllegalArgumentException {
			int colon = line.indexOf(':');
			if (colon == -1)
				throw new IllegalArgumentException("http header without ':', line=" + line);
			name = line.substring(0, colon).trim();
			value = line.substring(colon + 1).trim();
		}

		public String toString() {
			return name + ": " + value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}
	}

	// Method SP Request-URI SP HTTP-Version CRLF
	public static class RequestLine {
		String method;
		String requestURI;
		String httpVersion;

		public String getMethod() {
			return method;
		}

		public String getRequestURI() {
			return requestURI;
		}

		public String getHttpVersion() {
			return httpVersion;
		}

		public RequestLine(String method, String requestURI, String httpVersion) {
			this.method = method;
			this.requestURI = requestURI;
			this.httpVersion = httpVersion;
		}

		public String toString() {
			return method + " " + requestURI + " " + httpVersion;
		}

		public RequestLine(String line) throws NoSuchFieldException {
			StringTokenizer st = new StringTokenizer(line);
			method = st.nextToken();
			requestURI = st.nextToken();
			httpVersion = st.nextToken();
		}
	}

	public static class StatusLine {
		public String getHttpVersion() {
			return httpVersion;
		}

		public int getStatusCode() {
			return statusCode;
		}

		public String getReasonPhrase() {
			return reasonPhrase;
		}

		String httpVersion;
		int statusCode;
		String reasonPhrase;

		public String toString() {
			return httpVersion + " " + statusCode + " " + reasonPhrase;
		}

		public StatusLine(String httpVersion, int statusCode, String reasonPhrase) throws IllegalArgumentException {
			if (statusCode < 100 || statusCode > 999)
				throw new IllegalArgumentException("status code must be XXX");
			this.httpVersion = httpVersion;
			this.statusCode = statusCode;
			this.reasonPhrase = reasonPhrase;
		}

		public StatusLine(String line){
			int colon1 = line.indexOf(' ');
			if (colon1 == -1)
				throw new IllegalArgumentException("wrong status line - no the 1st space");
			httpVersion = line.substring(0, colon1);
			int colon2 = line.indexOf(' ', colon1 + 1);
			if (colon2 == -1)
				throw new IllegalArgumentException("wrong status line - no the 2nd space");
			String strStatusCode = line.substring(colon1 + 1, colon2);
			statusCode = Integer.parseInt(strStatusCode);
			if (statusCode < 100 || statusCode > 999)
				throw new IllegalArgumentException("status code must be XXX");
			reasonPhrase = line.substring(colon2 + 1);
		}
	}
}
