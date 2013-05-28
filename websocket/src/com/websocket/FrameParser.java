package com.websocket;

import java.io.*;
import java.util.Arrays;
import java.util.Random;
/*
0                   1                   2                   3
0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+
FIN: Indicates that this is the final fragment in a message.
RSV1 RSV2 RSV3 reserved, must be 0
Opcode:  4 bits Defines the interpretation of the "Payload data.

*/
public class FrameParser {
  final static Random random = new Random();

	// first byte bits:
	private static final int FIN  = (1<<7);
	private static final int RSV  = 0x70;
	private static final int OPCODE = 0x0F;
	// the second byte bits: 
	private static final int MASK = (1 << 7);
	private static final int LENGTH = 0x7F;

	// operation codes
	static final int OP_CONTINUATION = 0;
	static final int OP_TEXT = 1;
	static final int OP_BINARY = 2;
	static final int OP_CLOSE = 8;
	static final int OP_PING = 9;
	static final int OP_PONG = 10;

	// to check
	private static final int OPCODES[] = { OP_CONTINUATION, OP_TEXT, OP_BINARY, OP_CLOSE, OP_PING, OP_PONG };
	private static final int CTRL_OPCODES[] = { OP_CLOSE, OP_PING, OP_PONG };


	public static class Frame {
	  byte [] payload;
	  int     opCode;
	  boolean isMasked;
		boolean isFinal;
	}
	
	private static void useMask(byte[] payload, byte[] mask, int offset) {
    for (int i = 0; i < payload.length - offset; i++) {
        payload[offset + i] = (byte) (payload[offset + i] ^ mask[i % 4]);
    }
  }
	
	/**
	 *  Read and parse frame
	 */
	public static Frame readFrame(DataInputStream stream) throws IOException, EOFException {
		  Frame frame = new Frame();
		  
		  // read the first byte (op code)
		  int data = stream.readUnsignedByte(); 
		  frame.isFinal = (data & FIN) != 0;
			boolean isReserved = (data & RSV) != 0;
			if( isReserved ){
				throw new IOException("RSV not zero");
			}

			frame.opCode = (data & OPCODE);

			if (Arrays.binarySearch(OPCODES, frame.opCode) < 0) {
				throw new IOException("Bad opcode");
			}
			if (Arrays.binarySearch(CTRL_OPCODES, frame.opCode) >= 0 && !frame.isFinal) {
				throw new IOException("In control opcode, must set FIN");
			}
			
			// read the second byte (mask and payload length)
		  data = stream.readUnsignedByte(); 
			
			frame.isMasked = (data & MASK) != 0;
			int length = (data & LENGTH);
      
			// read extended length if need
			if( length < 126 ){
				// short length is already read.
			} else if( length == 126 ){
      	length = stream.readUnsignedShort(); // read 2 bytes length
      } else if(length == 127){
        long length8 = stream.readLong();  // read 8 bytes length
        if( length8 > Integer.MAX_VALUE )
        	throw new IOException("too big frame length");
        length = (int)length8;
      }

			byte [] mask = null;
			if( frame.isMasked ){
				mask = new byte[4];
				stream.readFully(mask);
			} 
			
			frame.payload = new byte[length]; // can be optimized.
			stream.readFully(frame.payload);
			
			if( frame.isMasked ){
			  useMask(frame.payload, mask, 0);	
			}
			return frame;
	}



	public static byte [] buildFrame(byte [] buffer, int opcode, int errorCode, boolean isMasked, boolean isFinal) {
		if( buffer == null )
			buffer = new byte[0];
		int insert = (errorCode > 0) ? 2 : 0;
		int length = buffer.length + insert;
		int header = (length <= 125) ? 2 : (length <= 0xFFFF ? 4 : 10);
		int offset = header + (isMasked ? 4 : 0);
		byte[] frame = new byte[length + offset];
		int masked = isMasked ? MASK : 0;
		int finbit = isFinal ? FIN : 0;

		frame[0] = (byte) ((byte)(finbit) | (byte) opcode); // always create only one frame

		if (length <= 125) {
			frame[1] = (byte) (masked | length);
		} else if (length <= 65535) {
			frame[1] = (byte) (masked | 126);
			frame[2] = (byte) (length >> 8);
			frame[3] = (byte) (length & 0xFF);
		} else {
			frame[1] = (byte) (masked | 127);
			//frame[2] = (byte) (0);
			//frame[3] = (byte) (0);
			//frame[4] = (byte) (0);
			//frame[5] = (byte) (0);
			frame[6] = (byte) ((length  >> 24) & 0xFF);
			frame[7] = (byte) ((length  >> 16) & 0xFF);
			frame[8] = (byte) ((length >> 8) & 0xFF);
			frame[9] = (byte) (length & 0xFF);
		}

		if (errorCode > 0) {
			frame[offset] = (byte) ((errorCode >> 8) & 0xFF);
			frame[offset + 1] = (byte) (errorCode & 0xFF);
		}
		System.arraycopy(buffer, 0, frame, offset + insert, buffer.length);

		if (isMasked) {
			byte[] mask = new byte[4];
			random.nextBytes(mask);
			System.arraycopy(mask, 0, frame, header, mask.length);
			useMask(frame, mask, offset);
		}

		return frame;
	}
}