import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private static JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    // Socket info
    static private SocketChannel SChannel;
	static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    
    // Decoder and enconder for transmitting text
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private final CharsetEncoder encoder = charset.newEncoder();
    static private String comsent = "";
      


    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public static void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        try {
            SChannel = SocketChannel.open();
            SChannel.configureBlocking(true);
            SChannel.connect(new InetSocketAddress(server, port));
          } catch (IOException ex) {
          }


    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
    	String[] args = message.split(" ");
    	comsent = args[0];
    	printMessage(message + "\n");
    	SChannel.write(encoder.encode(CharBuffer
				.wrap(message + "\n")));
    	
    }

    
    // Método principal do objecto
    public void run() throws IOException {
    	try {
    	      while (!SChannel.finishConnect()) {
    	      }
        } catch (Exception e) {
            printMessage("Can't connect, try again later");
        }
    	while(true)
    		if(processInput()){}
    		else{break;}
    	
    	SChannel.close();

        try {
          // To prevent client from closing right away
          Thread.sleep(10);
        } catch (InterruptedException ie) {
        }
        
		// If no data, close the connection
        System.exit(0);
    }
    
	static private boolean processInput() throws IOException {
		// Read the message to the buffer

		buffer.clear();
		SChannel.read(buffer);
		buffer.flip();
		// If no data, close the connection
		if (buffer.limit() == 0) {
			return false;
		}

		// Decode and print the message to stdout
		String message = decoder.decode(buffer).toString();
		message=adaptmessage(message);
		printMessage(message);

		return true;
	}
	
	static private String adaptmessage(String message) throws IOException {
		
		int j = message.length()-1;
		message = message.substring(0, j);
		String args[] = message.split(" ");
		if(comsent.contains("nick")){
			if(!message.contains("ERROR"))
			message = "You have changed your nickname successfully\n";
			else{
				message = "That nickname already exists\n";
			}
		}
		else if(message.contains("NEWNICK")){
			message = args[1] + " changed nickname to " + args[2] + "\n";
		}
		else if(comsent.contains("join") || args[0].equals("JOINED")){
			if(args[0].contains("OK"))
				message = "You have joined the room\n";
			else if(message.contains("ERROR")){
				message = "Need to have a NICKNAME\n";
			}
			else
				message = args[1] + " has joined the room\n";
		}
		else if(message.contains("LEFT")){
			message = args[1] + " has left the room\n";
		}
		else if(comsent.contains("bye")){
			message = "Closing connection\n";
		}
		else if(comsent.contains("leave")){
			if(args[0].contains("OK"))
				message = "Succesfully\n";
			else
				message = "Not in room\n";
		}
		else if(message.contains("priv")){
			message = args[1] + " has sent you a private message: " + message.substring(args[0].length() + args[1].length() + 1) + "\n";
		}
		else if(comsent.contains("priv")){
			if(args[0].contains("OK"))
				message = "Succesfully\n";
			else
				message = "Not online\n";
		}
		else if(message.contains("ERROR")){
			message = "You need to bee inside a room to send a message\n";
		}
		else if(message.contains("MESSAGE")){
			message = args[1] + ":" + message.substring(args[0].length() + args[1].length() + 1) + "\n";
		}
		comsent = "";
		
		
		return message;
	}
    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

    
}
