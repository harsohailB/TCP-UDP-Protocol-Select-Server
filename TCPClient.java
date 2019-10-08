/*
 * A simple TCP client that sends messages to a server and display the message
   from the server. 
 * For use in CPSC 441 lectures
 * Instructor: Prof. Mea Wang
 * 
 * Implementation of commands and connection of TCP and UDP Clients simultaneously
 * by Harsohail Brar
 */

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TCP Client class that connects to a select server with a socket
 */
class TCPClient {

    /**
     * Main function
     * @param args first argument = IP of server, second argument = PORT of server
     * @throws Exception
     */
    public static void main(String args[]) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: TCPClient <Server IP> <Server Port>");
            System.exit(1);
        }

        // Initialize a client socket connection to the server
        Socket clientSocket = new Socket(args[0], Integer.parseInt(args[1]));

        // Initialize input and an output stream for the connection(s)
        PrintWriter outBuffer = new PrintWriter(clientSocket.getOutputStream(), true);

        BufferedReader inBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        // Initialize user input stream
        String line;
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

        // Get user input and send to the server
        // Display the echo meesage from the server
        System.out.print("Please enter a message to be sent to the server ('logout' to terminate): ");
        line = inFromUser.readLine();
        String files = "";
        while (!line.equals("logout")) {
            String[] lineArr = line.split(" ");

            // Send to the server
            outBuffer.println(line);

            // Getting response from the server
            line = inBuffer.readLine();
            System.out.println("Server: " + line);

            // "Terminate command"
            if (line.equals("terminate")) {
                System.out.println("Server Terminated: Closing Socket");
                break;
            }

            // "List" command (recieve file names)
            if (line.equals("list")) {
                files = "";
                System.out.print("-----Files-----\n");
                files += inBuffer.readLine() + "\n";
                while (inBuffer.ready()) {
                    files += inBuffer.readLine() + "\n";
                }
                System.out.print(files);
                System.out.println("---------------");
            }

            // "Get" command (recieve file)
            line = line.replace("\n", "");
            lineArr = line.split(" ");
            if (lineArr[0].equals("get")) {
                // Create file name with port
                String fileName = lineArr[1] + "-" + args[1];

                // check if file exists
                String[] fileNames = files.split("\n");
                boolean fileExists = false;
                if(fileNames.length == 0)
                    break;

                for (String name : fileNames) {
                    if (name.equals(lineArr[1])) {
                        fileExists = true;
                        break;
                    }
                }

                // checks if file exists
                if (fileExists) {
                    String fileSize = inBuffer.readLine();
                    Path currentPath = Paths.get("");
                    String path = currentPath.toAbsolutePath().toString() + "/" + fileName;
                    File file = new File(path);
                    file.createNewFile();
                    FileOutputStream fos = new FileOutputStream(path);

                    fos.write(inBuffer.read());
                    while (inBuffer.ready()) {
                        fos.write(inBuffer.read());
                    }

                    System.out.println("File saved in " + file.getName() + " (" + fileSize + "bytes)");
                    fos.close();
                }else{
                    System.out.println("File doesn't Exist");
                }
            }

            System.out.print("Please enter a message to be sent to the server ('logout' to terminate): ");
            line = inFromUser.readLine();
        }

        // Close the socket
        clientSocket.close();
    }
}
