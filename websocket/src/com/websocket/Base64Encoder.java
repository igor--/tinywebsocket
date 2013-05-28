package com.websocket;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Base64Encoder extends FilterOutputStream {
	public static byte [] doEncode(byte [] b) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Base64Encoder encoder = new Base64Encoder(baos, -1);
		encoder.write(b, 0, b.length);
		encoder.flush();
		return baos.toByteArray();
	}
	
	
	private byte[] buffer;
	private int bufsize;
	private int count;
	private int bytesPerLine;
	private static final char[] pem_array = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I',
			'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y',
			'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
			'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4',
			'5', '6', '7', '8', '9', '+', '/'};

	public Base64Encoder(OutputStream outputstream) {
		this(outputstream, 76);
	}

	public Base64Encoder(OutputStream outputstream, int i) {
		super(outputstream);
		buffer = new byte[3];
		bytesPerLine = i;
	}

	public void write(byte[] abyte0, int i, int j) throws IOException {
		for (int k = 0; k < j; k++) {
			write(abyte0[i + k]);
		}
	}

	public void write(byte[] abyte0) throws IOException {
		write(abyte0, 0, abyte0.length);
	}

	public void write(int i) throws IOException {
		buffer[bufsize++] = (byte) i;
		if (bufsize == 3) {
			encode();
			bufsize = 0;
		}
	}

	public void flush() throws IOException {
		if (bufsize > 0) {
			encode();
			bufsize = 0;
		}
		out.flush();
	}

	public void close() throws IOException {
		flush();
		out.close();
	}

	private void encode() throws IOException {
		if(bytesPerLine > 0 && count + 4 > bytesPerLine) {
			out.write(13);
			out.write(10);
			count = 0;
		}
		if (bufsize == 1) {
			byte byte0 = buffer[0];
			int i = 0;
			//boolean flag = false;
			out.write(pem_array[byte0 >>> 2 & 0x3f]);
			out.write(pem_array[(byte0 << 4 & 0x30) + (i >>> 4 & 0xf)]);
			out.write(61);
			out.write(61);
		}
		else if (bufsize == 2) {
			byte byte1 = buffer[0];
			byte byte3 = buffer[1];
			int j = 0;
			out.write(pem_array[byte1 >>> 2 & 0x3f]);
			out.write(pem_array[(byte1 << 4 & 0x30) + (byte3 >>> 4 & 0xf)]);
			out.write(pem_array[(byte3 << 2 & 0x3c) + (j >>> 6 & 0x3)]);
			out.write(61);
		}
		else {
			byte byte2 = buffer[0];
			byte byte4 = buffer[1];
			byte byte5 = buffer[2];
			out.write(pem_array[byte2 >>> 2 & 0x3f]);
			out.write(pem_array[(byte2 << 4 & 0x30) + (byte4 >>> 4 & 0xf)]);
			out.write(pem_array[(byte4 << 2 & 0x3c) + (byte5 >>> 6 & 0x3)]);
			out.write(pem_array[byte5 & 0x3f]);
		}
		count += 4;
	}

	public static byte[] encode(byte[] abyte0) {
		if (abyte0.length == 0) {
			return abyte0;
		}
		int i = 0;
		int j = 0;
		byte[] abyte1 = new byte[((abyte0.length + 2) / 3) * 4];
		for (int k = abyte0.length; k > 0; k -= 3) {
			if (k == 1) {
				byte byte0 = abyte0[i++];
				int l = 0;
				//boolean flag = false;
				abyte1[j++] = (byte) pem_array[byte0 >>> 2 & 0x3f];
				abyte1[j++] = (byte) pem_array[(byte0 << 4 & 0x30) + (l >>> 4 & 0xf)];
				abyte1[j++] = 61;
				abyte1[j++] = 61;
			}
			else if (k == 2) {
				byte byte1 = abyte0[i++];
				byte byte3 = abyte0[i++];
				int i1 = 0;
				abyte1[j++] = (byte) pem_array[byte1 >>> 2 & 0x3f];
				abyte1[j++] = (byte) pem_array[(byte1 << 4 & 0x30) + (byte3 >>> 4 & 0xf)];
				abyte1[j++] = (byte) pem_array[(byte3 << 2 & 0x3c) + (i1 >>> 6 & 0x3)];
				abyte1[j++] = 61;
			}
			else {
				byte byte2 = abyte0[i++];
				byte byte4 = abyte0[i++];
				byte byte5 = abyte0[i++];
				abyte1[j++] = (byte) pem_array[byte2 >>> 2 & 0x3f];
				abyte1[j++] = (byte) pem_array[(byte2 << 4 & 0x30) + (byte4 >>> 4 & 0xf)];
				abyte1[j++] = (byte) pem_array[(byte4 << 2 & 0x3c) + (byte5 >>> 6 & 0x3)];
				abyte1[j++] = (byte) pem_array[byte5 & 0x3f];
			}
		}
		return abyte1;
	}
}
