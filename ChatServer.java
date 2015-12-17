import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();
  static private final CharsetEncoder encoder = charset.newEncoder();
  
  
  //saved maps
  static private final HashMap<SocketChannel,CUser> users = new HashMap <SocketChannel,CUser>();
  static private HashMap<String, CUser> usernames = new HashMap<String, CUser>();
  static private HashMap<String, CRoom> roomMap = new HashMap<String, CRoom>();
  
  
  //pattern matching for messages of commands
  static private final String nickRegex = "nick .+";
  static private final String joinRegex = "join .+";
  static private final String leaveRegex = "leave.*";
  static private final String byeRegex = "bye.*";
  static private final String privateRegex = "priv .+ .+";


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
            SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from " + s );
	    
            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
	    sc.configureBlocking( false );
	   

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );
            //FALTAAQUIOADDSC


          } else if ((key.readyOps() & SelectionKey.OP_READ) ==
            SelectionKey.OP_READ) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput(sc);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }

  // Helper function to send a message
  static private void sendMessage(SocketChannel sc, ChatMessage message) throws IOException {
    sc.write(encoder.encode(CharBuffer.wrap(message.toString())));
  }

  // Send message message
  static private void sendMessageMessage(CUser receiver, String sender, String messageValue) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.MESSAGE, sender, messageValue);
    sendMessage(receiver.getSc(), message);
  }

  // Send error message
  static private void sendErrorMessage(CUser receiver, String errorMessage) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.ERROR, errorMessage);
    sendMessage(receiver.getSc(), message);
  }

  // Send ok message
  static private void sendOkMessage(CUser receiver) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.OK);
    sendMessage(receiver.getSc(), message);
  }

  // Send newnick message
  static private void sendNewnickMessage(CUser receiver, String oldNick, String newNick) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.NEWNICK, oldNick, newNick);
    sendMessage(receiver.getSc(), message);
  }

  // Send joined message
  static private void sendJoinedMessage(CUser receiver, String joinNick) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.JOINED, joinNick);
    sendMessage(receiver.getSc(), message);
  }

  // Send left message
  static private void sendLeftMessage(CUser receiver, String leftNick) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.LEFT, leftNick);
    sendMessage(receiver.getSc(), message);
  }

  // Send bye message
  static private void sendByeMessage(CUser receiver) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.BYE);
    sendMessage(receiver.getSc(), message);
  }

  // Send private message
  static private void sendPrivateMessage(CUser receiver, String sender, String messageValue) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.PRIVATE, sender, messageValue);
    sendMessage(receiver.getSc(), message);
  }


  // Just read the message from the socket and send it to stdout
    static private boolean processInput( SocketChannel sc) throws IOException {
    // Read the message to the buffer
    
    buffer.clear();
    sc.read( buffer );
    buffer.flip();
    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
      }

    
    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString().trim();
    CUser sender = (CUser)users.get(sc);

    if (message.startsWith("/")) {
      String escapedMessage = message.substring(1);
      String command = escapedMessage.trim();
      
      if (Pattern.matches(nickRegex, command)) {
	        sendNickCommand(sender, command.split(" ")[1]);
	      } else if (Pattern.matches(joinRegex, command)) {
	        sendJoinCommand(sender, command.split(" ")[1]);
	      } else if (Pattern.matches(leaveRegex, command)) {
	        sendLeaveCommand(sender);
	      } else if (Pattern.matches(byeRegex, command)) {
	        sendByeCommand(sender);
	      } else if (Pattern.matches(privateRegex, command)) {
	        sendPrivateCommand(sender, command.split(" ")[1], command.split(" ")[2]);
	      } else if (command.startsWith("/")) {
	        sendSimpleMessage(sender, escapedMessage);
	      } else {
	    	sendErrorMessage(sender, "Unknown command");
	      }
      } else {
    	  sendSimpleMessage(sender, message);
      }
    
   
      return true;
  }
}