import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import com.websocket.WebSocket;
import com.websocket.WebSocketListener;

public class ServerTest {
	static WebSocketListener listener = new WebSocketListener(){
		
		@Override
		public void onConnect(WebSocket ws) {
			System.out.println("websocket connected");
			System.out.println("uri=" + ws.getURI().toString());
			System.out.println("Host=" + ws.getHost());
			System.out.println("Origin=" + ws.getOrigin());
			ws.send("Hello from test echo server !");
		}

		@Override
		public void onMessage(WebSocket ws, String message) {
		  System.out.println("received message: " + message);
		  ws.send(message);
		}

		@Override
		public void onMessage(WebSocket ws, byte[] data) {
		  System.out.println("received bynary message: " + Arrays.toString(data));
		  ws.send(data);
		}

		@Override
		public void onDisconnect(WebSocket ws, int code, String reason) {
      System.out.println("on disconnect " + reason);			
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
		  System.out.println("on error " + error);	
		}

		@Override
		public void onPong(WebSocket ws, byte [] data) {
		  if( data.length == 0 )
		  	System.out.println("on pong (without data)");
		  else 
		  	System.out.println("on pong :" + WebSocket.bytesToString(data));
		}
	}; 
	/**
	 * echo test
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		ServerSocket serverSocket = new ServerSocket(80);
		for(;;){
		  Socket socket = serverSocket.accept();	
			WebSocket ws = WebSocket.createServerWebSocket(socket, listener);
			ws.connect();
		}
	}
}
