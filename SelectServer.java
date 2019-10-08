/*
 * A simple TCP select server that accepts multiple connections and echo message back to the clients
 * For use in CPSC 441 lectures
 * Instructor: Prof. Mea Wang
 * 
 * Implementation of commands and connection of TCP and UDP Clients simultaneously
 * by Harsohail Brar
 */

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SelectServer class which allows TCP and UDP clients to connect
 * and interact at the same time using a selector
 */
public class SelectServer {
    //Constants
    public static int BUFFERSIZE = 32;
    public static int PORT;

    //Buffers
    private String line;
    private Charset charset;
    private CharsetDecoder decoder;
    private CharsetEncoder encoder;
    private ByteBuffer inBuffer;
    private ByteBuffer outBuffer;
    private CharBuffer cBuffer;
    private int bytesSent, bytesRecv;

    //Selector
    private Selector selector;
    private InetSocketAddress isa;
    private Set readyKeys;
    private Iterator readyItor;
    private SelectionKey key;
    private Channel keyChannel;

    //TCP Member Variables
    private ServerSocketChannel tcpServer;
    private SocketChannel cchannel;

    //UDP Member Variables
    private DatagramChannel udpServer;
    private SocketAddress clientAddress;
    
    private boolean terminated;

    /**
     * Constructor for select server
     */
    public SelectServer() {
        line = "";
        charset = Charset.forName("us-ascii");
        decoder = charset.newDecoder();
        encoder = charset.newEncoder();
        inBuffer = null;
        outBuffer = null;
        cBuffer = null;
    }

    /**
     * Main function
     */
    public static void main(String args[]) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: UDPServer <Listening Port>");
            System.exit(1);
        }

        PORT = Integer.parseInt(args[0]);

        SelectServer server = new SelectServer();
        server.run();
    }

    /**
     * This functions runs the server and calls any 
     * nessecary member functions
     * @throws Exception 
     */
    public void run() throws Exception{
        // Initialize the selector
        selector = Selector.open();

        // Create a tcp server channel and make it non-blocking
        tcpServer = ServerSocketChannel.open();
        tcpServer.configureBlocking(false);

        // Get the port number and bind the socket
        isa = new InetSocketAddress(PORT);
        tcpServer.socket().bind(isa);

        // Register that the tcp server selector is interested in connection requests
        tcpServer.register(selector, SelectionKey.OP_ACCEPT);

        // create a UDP server channel and make it non-blocking
        udpServer = DatagramChannel.open();
        udpServer.configureBlocking(false);

        // Get the port number and bind the socket
        udpServer.socket().bind(isa);

        // Register that the udp server selector is interested in connection requests
        udpServer.register(selector, SelectionKey.OP_READ);
        System.out.println(InetAddress.getLocalHost());

        // Wait for something happen among all registered sockets
        try {
            terminated = false;
            boolean validCommand = false;
            while (!terminated) {
                if (selector.select(500) < 0) {
                    System.out.println("select() failed");
                    System.exit(1);
                }

                // Get set of ready sockets
                readyKeys = selector.selectedKeys();
                readyItor = readyKeys.iterator();

                // Walk through the ready set
                while (readyItor.hasNext()) {
                    // Get key from set
                    key = (SelectionKey) readyItor.next();

                    // Store channel associated with key
                    keyChannel = (Channel) key.channel();

                    // Remove current entry
                    readyItor.remove();

                    // Accept new connections, if any
                    if (key.isAcceptable() && keyChannel == tcpServer) {
                        acceptNewConnections();
                    } else {
                        // UDP Recieve
                        if (key.isReadable() && keyChannel == udpServer) {
                            echoUDP();
                            checkTerminateCommand();
                        }
                        // TCP Recieve
                        else if (key.isReadable()) {
                            validCommand = echoTCP();
                            
                            if(validCommand){
                                // check correct read
                                if (bytesRecv <= 0) {
                                    continue;
                                }

                                // check if echo is successful
                                if (bytesSent != bytesRecv) {
                                    System.out.println("write() error, or connection closed");
                                    key.cancel(); // deregister the socket
                                    continue;
                                }
                            }
                            
                            // Removes characters added by PrintWriter on client side
                            line = line.replace("\n", "");
                            //line = line.substring(0, line.length() - 1);

                            // "terminate" command
                            checkTerminateCommand();

                            // "list" Command
                            checkListCommand();

                            // "Get File" Command
                            checkGetFileCommand();
                        }
                    }
                } // end of while (readyItor.hasNext())
            } // end of while (!terminated)
        } catch (IOException e) {
            System.out.println(e);
        }

        // close all connections
        Set keys = selector.keys();
        Iterator itr = keys.iterator();
        while (itr.hasNext()) {
            SelectionKey key = (SelectionKey) itr.next();
            // itr.remove();
            if (key.isAcceptable())
                ((ServerSocketChannel) key.channel()).socket().close();
            else if (key.isValid())
                key.channel().close();
        }
    }

    /**
     * Accepts any new connect from a TCP Client
     */
    public void acceptNewConnections() throws Exception{
        SocketChannel cchannel = ((ServerSocketChannel) key.channel()).accept();
        cchannel.configureBlocking(false);
        System.out.println("Accept connection from " + cchannel.socket().toString());

        // Register the new connection for read operation
        cchannel.register(selector, SelectionKey.OP_READ);
    }

    /**
     * Echos the message back to the UDP Client
     */
    public void echoUDP() throws Exception{
        // Open input and output streams
        inBuffer = ByteBuffer.allocateDirect(BUFFERSIZE);
        cBuffer = CharBuffer.allocate(BUFFERSIZE);
        clientAddress = udpServer.receive(inBuffer);

        inBuffer.flip(); // make buffer available
        decoder.decode(inBuffer, cBuffer, false);
        cBuffer.flip();
        line = cBuffer.toString();
        System.out.println("UDP Client: " + line);

        // Echo the message back
        if (clientAddress != null) {
            inBuffer.flip();
            udpServer.send(inBuffer, clientAddress);
        }
    }

    /**
     * Echos the message back to the TCP Client
     */
    public boolean echoTCP() throws Exception{
        cchannel = (SocketChannel) key.channel();
        Socket socket = cchannel.socket();

        // Open input and output streams
        inBuffer = ByteBuffer.allocateDirect(BUFFERSIZE);
        cBuffer = CharBuffer.allocate(BUFFERSIZE);

        // Read from socket
        bytesRecv = cchannel.read(inBuffer);
        if (bytesRecv <= 0) {
            System.out.println("read() error, or connection closed");
            key.cancel(); // deregister the socket
            return false;
        }

        inBuffer.flip(); // make buffer available
        decoder.decode(inBuffer, cBuffer, false);
        cBuffer.flip();
        line = cBuffer.toString();
        System.out.print("TCP Client: " + line);

        String[] lineArr = line.split(" ");
        if(lineArr.length > 0){
            if(!line.equals("list\n") && !line.equals("logout\n") && !line.equals("terminate\n")){
                if(!lineArr[0].equals("get")){
                    // Send error message to client
                    String error = "Unknown command: " + line;
                    outBuffer = ByteBuffer.wrap(error.getBytes(charset));
                    cchannel.write(outBuffer);
                    return false;
                }
            }
        }

        // Echo the message back
        inBuffer.flip();
        bytesSent = cchannel.write(inBuffer);
        return true;
    }

    /**
     * Checks and responds to terminate command by the client
     * Changes terminated boolean to true causing the server
     * to terminate
     */
    public void checkTerminateCommand() throws Exception{
        // "Terminate" Command
        if (line.equals("terminate")) {
            System.out.println("Terminating");
            terminated = true;
        }
    }

    /**
     * Checks and responds to a "list" command by the client
     * Sends file names in current directory to client
     */
    public void checkListCommand() throws Exception{
        if (line.equals("list")) {
            System.out.println("Sending File Names");
            Path currentPath = Paths.get("");
            String path = currentPath.toAbsolutePath().toString();

            File folder = new File(path);
            String[] files = folder.list();
            String fileNames = "";
            for (String file : files) {
                fileNames += file + "\n";
            }

            // Send file names
            outBuffer = ByteBuffer.allocateDirect(BUFFERSIZE);
            outBuffer.flip();
            outBuffer = ByteBuffer.wrap(fileNames.getBytes(charset));
            cchannel.write(outBuffer);
        }
    }

    /**
     * Check and responds to a "get file" command by the client
     * Sends requested file to client
     */
    public void checkGetFileCommand() throws Exception{
        String[] input = line.split(" ");
        if (input[0].equals("get")) {
            Path currentPath = Paths.get("");
            String path = currentPath.toAbsolutePath().toString() + "/";

            File folder = new File(path);
            String[] files = folder.list();
            String fileNames = "";
            for (String file : files) {
                fileNames += file + "\n";
            }

            System.out.println("Open File: " + input[1]);
            String[] fileNamesArr = fileNames.split("\n");
            boolean fileExists = false;
            for (String s : fileNamesArr) {
                // Check if file exists
                if (input[1].equals(s)) {
                    File file = new File(path + input[1]);
                    byte[] byteArray = new byte[(int) file.length()];
                    FileInputStream fis = new FileInputStream(file);
                    fis.read(byteArray);
                    String fileSize = String.valueOf(byteArray.length) + "\n";

                    // Send file
                    outBuffer = ByteBuffer.allocateDirect(BUFFERSIZE);
                    outBuffer.flip();
                    // Send file size
                    outBuffer = ByteBuffer.wrap(fileSize.getBytes(charset));
                    cchannel.write(outBuffer);
                    // Send file data
                    outBuffer = ByteBuffer.wrap(byteArray);
                    cchannel.write(outBuffer);
                    fileExists = true;
                    break;
                }
            }
            if(!fileExists){
                System.out.println("open failed()");
            }
        }
    }
}
