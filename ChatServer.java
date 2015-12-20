import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
	// A pre-allocated buffer for the received data
	static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

	// Decoder for incoming text -- assume UTF-8
	static private final Charset charset = Charset.forName("UTF8");
	static private final CharsetDecoder decoder = charset.newDecoder();
	static private final CharsetEncoder encoder = charset.newEncoder();
	static private HashMap<SocketChannel, CUser> users = new HashMap<SocketChannel, CUser>();
	static private HashMap<String, CRoom> rooms = new HashMap<String, CRoom>();
	static private HashMap<String, CUser> registedUsers = new HashMap<String, CUser>();
	
	static private String incomplete_message = new String("");
	static private boolean complete = false;

	static public void main(String args[]) throws Exception {
		// Parse port from command line
		int port = Integer.parseInt(args[0]);

		try {
			// Instead of creating a ServerSocket, create a ServerSocketChannel
			ServerSocketChannel ssc = ServerSocketChannel.open();

			// Set it to non-blocking, so we can use select
			ssc.configureBlocking(false);

			// Get the Socket connected to this channel, and bind it to the
			// listening port
			ServerSocket ss = ssc.socket();
			InetSocketAddress isa = new InetSocketAddress(port);
			ss.bind(isa);

			// Create a new Selector for selecting
			Selector selector = Selector.open();

			// Register the ServerSocketChannel, so we can listen for incoming
			// connections
			ssc.register(selector, SelectionKey.OP_ACCEPT);
			System.out.println("Listening on port " + port);

			while (true) {
				// See if we've had any activity -- either an incoming
				// connection,
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
					if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {

						// It's an incoming connection. Register this socket
						// with
						// the Selector so we can listen for input on it
						Socket s = ss.accept();
						System.out.println("Got connection from " + s);

						// Make sure to make it non-blocking, so we can use a
						// selector
						// on it.
						SocketChannel sc = s.getChannel();
						sc.configureBlocking(false);

						// Register it with the selector, for reading
						sc.register(selector, SelectionKey.OP_READ);
						CUser newUser = new CUser(null, State.INIT, null, sc);
						users.put(sc, newUser);

					} else if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {

						SocketChannel sc = null;

						try {

							// It's incoming data on a connection -- process it
							sc = (SocketChannel)key.channel();
							boolean ok = processInput(sc);

							// If the connection is dead, remove it from the
							// selector
							// and close it
							if (!ok) {
								key.cancel();

								Socket s = null;
								try {
									s = sc.socket();
									if(users.get(sc).getSt() == State.INSIDE) 
										closeCon(users.get(sc), sc);
									System.out.println("Closing connection to "
											+ s);
									s.close();
								} catch (IOException ie) {
									System.err.println("Error closing socket "
											+ s + ": " + ie);
								}
							}

						} catch (IOException ie) {

							// On exception, remove this channel from the
							// selector
							key.cancel();

							try {
								sc.close();
							} catch (IOException ie2) {
								System.out.println(ie2);
							}

							System.out.println("Closed " + sc);
						}
					}
				}

				// We remove the selected keys, because we've dealt with them.
				keys.clear();
			}
		} catch (IOException ie) {
			System.err.println(ie);
		}
	}

	// Just read the message from the socket and send it to stdout
	static private boolean processInput(SocketChannel sc) throws IOException {
		// Read the message to the buffer

		buffer.clear();
		sc.read(buffer);
		buffer.flip();
		
		// If no data, close the connection
		if (buffer.limit() == 0) {
			return false;
		}
		// Decode and print the message to stdout
		String message = decoder.decode(buffer).toString();
		   if(message.contains("\n")){
			      incomplete_message += message;
			      complete = true;
		   }
		   else{
			   incomplete_message+=message;
			   message = incomplete_message;
		   }
		   
		   if(complete){
			   useCommand(incomplete_message, sc);
			   incomplete_message = new String("");
			   complete=false;
		   }

		return true;
	}

	static private void useCommand(String message, SocketChannel sc)
			throws IOException {
		boolean aslash = message.charAt(0) == '/';
		int j = message.length() - 1;
		if (aslash)
			message = message.substring(1, j);
		String args[] = message.split(" ");
		if (args.length > 1 && aslash && args[0].charAt(0)!=('/'))
			message = message.substring(args[0].length() + 1);
		CUser submiter = users.get(sc);
		if (aslash) {
			// Nickname--------------------------------------------------
			if (args[0].equals("nick")) {
				if (registedUsers.containsKey(args[1])) {
					sc.write(encoder.encode(CharBuffer.wrap("ERROR\n")));
				} else {
					if (submiter.getSt() == State.INIT) {
						submiter.setSt(State.OUTSIDE);
					} else if (submiter.getSt() == State.INSIDE) {
						CRoom getroom = submiter.getRoom();
						CUser userarray[] = getroom.getUserArray();
						String nick = submiter.getNickname();
						for (CUser user : userarray) {
							if (user != submiter)
								user.getSc().write(
										encoder.encode(CharBuffer
												.wrap("NEWNICK " + nick + " "
														+ args[1] + "\n")));
						}
					}
					registedUsers.remove(submiter.getNickname());
					submiter.setNickname(args[1]);
					registedUsers.put(args[1], submiter);
					submiter.getSc().write(
							encoder.encode(CharBuffer.wrap("OK\n")));
				}
			}
			// Join------------------------------------------------------------------------------------------------------------------------------
			else if (args[0].equals("join")) {
				if (submiter.getSt() == State.INIT) {
					submiter.getSc().write(
							encoder.encode(CharBuffer.wrap("ERROR\n")));
				} else {
					if (!rooms.containsKey(args[1])) {
						CRoom newRoom = new CRoom(args[1]);
						rooms.put(args[1], newRoom);
					}

					String nick = submiter.getNickname();
					submiter.getSc().write(
							encoder.encode(CharBuffer.wrap("OK\n")));

					CRoom getroom = rooms.get(args[1]);
					getroom.joinUser(submiter);
					CUser[] userarray = getroom.getUserArray();
					for (CUser user : userarray) {
						if (user != submiter)
							user.getSc().write(
									encoder.encode(CharBuffer.wrap("JOINED "
											+ nick + "\n")));
					}

					if (submiter.getSt() == State.INSIDE) {
						CRoom lastroom = submiter.getRoom();
						lastroom.leftUser(submiter);
						CUser[] lastuserarray = lastroom.getUserArray();
						for (CUser lastuser : lastuserarray) {
							if (lastuser != submiter)
								lastuser.getSc().write(
										encoder.encode(CharBuffer.wrap("LEFT "
												+ nick + "\n")));
						}
					}
					if (submiter.getSt() == State.OUTSIDE)
						submiter.setSt(State.INSIDE);
					submiter.setRoom(rooms.get(args[1]));

				}
			} else if (args[0].charAt(0) == '/') {
				if (submiter.getSt() == State.INSIDE) {
					CRoom room = submiter.getRoom();
					CUser[] userarray = room.getUserArray();
					for (CUser user : userarray) {
						user.getSc().write(
								encoder.encode(CharBuffer.wrap("MESSAGE "
										+ submiter.getNickname() + " " + message
										+ "\n")));
					}
				}
			} else if (args[0].contains("leave")) {
				if (submiter.getSt() == State.INSIDE) {
					CRoom room = submiter.getRoom();
					room.leftUser(submiter);
					CUser[] userarray = room.getUserArray();
					for (CUser user : userarray) {
						if (user != submiter)
							user.getSc().write(
									encoder.encode(CharBuffer.wrap("LEFT "
											+ submiter.getNickname() + "\n")));
					}
					submiter.setSt(State.OUTSIDE);
					submiter.getSc().write(encoder.encode(CharBuffer.wrap("OK\n")));
				}
				else submiter.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
			} else if (args[0].equals("bye")) {
				closeCon(submiter, sc);
			} else if (args[0].equals("priv")) {
				message = message.substring(args[1].length() + 1);
				
				if (registedUsers.containsKey(args[1])) {
					submiter.getSc().write(encoder.encode(CharBuffer.wrap("OK\n")));
					registedUsers
							.get(args[1])
							.getSc()
							.write(encoder.encode(CharBuffer.wrap("PRIVATE "
									+ submiter.getNickname() + " " + message
									+ "\n")));
				}
				else submiter.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
			}
			else{
				submiter.getSc()
				.write(encoder.encode(CharBuffer.wrap("Not a valid command")));
			}

		} else {
			if (submiter.getSt() == State.INSIDE) {
				CRoom room = submiter.getRoom();
				CUser[] userarray = room.getUserArray();
				for (CUser user : userarray) {
					user.getSc().write(
							encoder.encode(CharBuffer.wrap("MESSAGE "
									+ submiter.getNickname() + " " + message)));
				}
			}
			else{
				submiter.getSc().write(
						encoder.encode(CharBuffer.wrap("ERROR\n")));
			}
		}
	}

	static private void closeCon(CUser submiter, SocketChannel sc)
			throws IOException {
		if(submiter.getSt()==State.INSIDE){
		submiter.getSc().write(encoder.encode(CharBuffer.wrap("BYE\n")));
		CRoom room = submiter.getRoom();
		room.leftUser(submiter);
		CUser[] userarray = room.getUserArray();
		for (CUser user : userarray) {
			if (user != submiter)
				user.getSc().write(
						encoder.encode(CharBuffer.wrap("Left "
								+ submiter.getNickname() + "\n")));
		}
		}
		else{
			submiter.getSc().write(
					encoder.encode(CharBuffer.wrap("BYE")));
		}
		Socket s = sc.socket();
		try {
			s = sc.socket();
			System.out.println("Closing connection to " + s);
			s.close();
		} catch (IOException ie) {
			System.err.println("Error closing socket " + s + ": " + ie);
		}
		if (users.containsKey(submiter.getNickname())) {
			users.remove(submiter.getNickname());
		}
		if (registedUsers.containsKey(submiter.getNickname())) {
			registedUsers.remove(submiter);
		}
		if (rooms.containsKey(submiter.getRoom())) {
			if (rooms.get(submiter.getRoom()).getUserArray().length == 0)
				rooms.remove(submiter.getRoom());
		}

	}
}
