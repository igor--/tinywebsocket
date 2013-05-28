import java.net.URI;
import java.util.Arrays;

import com.websocket.WebSocket;
import com.websocket.WebSocketListener;

/**
 * Test the client Web Socket 
 */
public class ClientTest {
	// Some long string
  static String someText = 
  		"Outer space, or simply space, is the void that exists between celestial bodies," +
      "including the Earth.[1] It is not completely empty, but consists of a hard vacuum" +
  		"containing a low density of particles: predominantly a plasma of hydrogen and helium," +
      "as well as electromagnetic radiation, magnetic fields, and neutrinos." +
  		"Observations have now recently proven that it also contains dark matter and dark energy." +
      "The baseline temperature, as set by the background radiation left over from the Big Bang," +
  		"is only 2.7 kelvin (K). Plasma with an extremely low density" +
      "(less than one hydrogen atom per cubic meter) and high temperature (millions of kelvin)" +
      " in the space between galaxies accounts for most of the baryonic (ordinary) matter in " +
      "outer space; local concentrations have condensed into stars and galaxies. " +
      "Intergalactic space takes up most of the volume of the Universe, " +
      "but even galaxies and star systems consist almost entirely of empty space.";
  		
  static WebSocket wsocket;
	static WebSocketListener listener = new WebSocketListener(){
  		
		@Override
		public void onConnect(WebSocket ws) {
		  wsocket.send("Hello !");
		  wsocket.send(someText);
		  wsocket.send(new byte []{1, 2});
		  wsocket.ping(null);
		  wsocket.ping(WebSocket.stringToBytes("This is my ping"));
		}

		@Override
		public void onMessage(WebSocket ws, String message) {
		  System.out.println("received message: " + message);
		}

		@Override
		public void onMessage(WebSocket ws, byte[] data) {
		  System.out.println("received bynary message: " + Arrays.toString(data));
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
	public static void main(String[] args) throws Exception {
		//URI uri = new URI("ws://echo.websocket.org/");
		//URI uri = new URI("http://cnn.com");
		URI uri = new URI("ws://192.168.0.161/");
    wsocket = WebSocket.createClientWebSocket(uri, listener, null);
    wsocket.connect();
    Thread.sleep(10000);
    wsocket.close(10, "closed by user");
    Thread.sleep(10000);
	}
}
