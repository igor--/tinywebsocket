package com.websocket;

public interface WebSocketListener {
  public void onConnect(WebSocket ws);
  public void onMessage(WebSocket ws, String message);
  public void onMessage(WebSocket ws, byte[] data);
  public void onDisconnect(WebSocket ws, int code, String reason);
  public void onError(WebSocket ws, Throwable error);
  public void onPong(WebSocket ws, byte [] data);
}
