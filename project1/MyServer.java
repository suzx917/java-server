/*
Author: Zixiu Su
ID: 1001820076
Date: 9/28/2020

This is a simple web server running on local handling HTTP 1.1 request (TCP).

Compile and run:
$ javac MyServer.java && java MyServer

Requirement:
JDK 1.8
Default port: 6789

The server uses a thread-per-request implementation. The main thread will start a socket
at designated port number and then a connection thread for each connection as long as 
total active connection is below the limit amount.
Each connection thread handles a TCP connection. If the number of active threads exceeds
max limit, the main thread will not start new connections (not accepting requests).

Server only responds to properly formatted GET requests and replies code 200 301 or 404,
only an index page (with its refs) will be served. See ConnectionHandler.run() for 
parsing logic.

Constants such as port number, max number of connections, etc, are specified in the 
members variables.

Java socket and thread usage learned from these text books:
Computer Networking: A Top-Down Approach (5th edition) - Kurose and Ross
Learning Network Programming with Java - Reese
*/

import java.io.*;
import java.util.stream.*;
import java.util.concurrent.*;
import java.net.*;

// This is the main class driving the server
class MyServer{
    ///////////////////// Server Settings /////////////////////

    // Server Info
    static final String SERVER_NAME = "localhost";
    static final int PORT_NUMBER = 6789;

    // Max number of connection threads
    static final int MAX_CONNECTIONS = 256;

    ///////////////////////////////////////////////////////////

    // Main function
    public static void main(String[] args) {
        try {
            ServerSocket welcomeSocket = new ServerSocket(PORT_NUMBER);
            System.out.println("Server started. Dir = " + System.getProperty("user.dir"));

            int test = 100;
            // Main loop
            while (test > 0) {
                // Check if reached max capacity then wait 1ms
                if (Thread.activeCount() > MAX_CONNECTIONS) {
                    System.out.println("Server waiting...");
                    Thread.sleep(1);
                    continue;
                }
                // Start a new socket and a corresponding thread
                try {
                    Socket connectionSocket = welcomeSocket.accept();
                    new Thread(new ConnectionHandler(connectionSocket)).start();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                // Update debug info
                System.out.format("Active Connections: %d/%d\n", Thread.activeCount(),
                                MAX_CONNECTIONS);
                test--;                
            }// End main loop
        } 
        catch (IOException e) {  // output exception info
            System.out.println("Failed to get server socket at port " + PORT_NUMBER);
            e.printStackTrace();
        } 
        catch (Throwable t) {  // catch all here
            t.printStackTrace();
        }
    }
}

    // This class stores some status lines for response headers
    class StatusLines {
        public static final String CODE400 = "HTTP/1.0 400 Bad request";
        public static final String CODE200 = "HTTP/1.0 200 OK";
        public static final String CODE301 = "HTTP/1.0 301 Moved Permanently";
        public static final String CODE404 = "HTTP/1.0 404 Not Found";
    }

// This class serves as a thread to handle the requests from the client socket
class ConnectionHandler implements Runnable {

    private long id;
    private Socket clientSocket; // Saving client socket passed by main thread
    private final String DEBUGINFO; // A prefix nametag for debug lines

    // Constructor
    public ConnectionHandler(Socket _s) {
        id = Thread.currentThread().getId();
        clientSocket = _s;
        DEBUGINFO = "Thread #"+id+" (client addr = "+clientSocket.getInetAddress()+"): ";
    }

    // Thread task function
    // Parse incoming HTTP request headers and send back proper responses
    @Override
    public void run() {
        // Debug info
        System.out.println(DEBUGINFO+"Started.");

        // Read received request from client socket
        try(BufferedReader inBuffer = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream())))
        {
            int lineNum = 1; // current line number of the header
            String line; // temp saving the current line of string
            
            // Open output writer to print text to client socket
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream());

            // For this project we only read the first request line
            while (lineNum < 6 && (line = inBuffer.readLine()) != null) {
                //
                // Parse HTTP header (only supports index page and images properly)
                //
                System.out.println(line);
                // If header ends
                if (line.equals("")) {
                    break;
                }
                
                if (lineNum == 1) { // request line
                    String[] tokens = line.split(" ");

                    // If not a GET Request discard it 
                    if (tokens.length != 3 || tokens[0].equals("GET") == false
                        || tokens[1].charAt(0) != '/') {
                        System.out.println(DEBUGINFO+"Invalid Request. "+line);
                        out.println(StatusLines.CODE400+"\r\n");
                        break;
                    }
                    // If requesting index
                    if (tokens[1].equals("/") || tokens[1].equals("/index.html")) {
                        out.println(StatusLines.CODE200);
                        out.println("Content-type: text/html");
                        out.println("Content-length: "+new File("index.html").length());
                        out.println(); // end header with blank line
                        out.flush();
                        WriteFileToSocket(new File("index.html"), clientSocket);
                    }
                    // If URL is "/301" then respond with code 301 (for demo purpose)
                    else if (tokens[1].equals("/301")) {
                        out.println(StatusLines.CODE301 + "\r\n");
                    }
                    // Other URL request
                    else {
                        // Check if file exists first
                        File file = new File("."+tokens[1]);
                        if (!file.exists()) {
                            out.println(StatusLines.CODE404 + "\r\n");
                        }
                        else{
                            // Decide content type
                            String type  = "";
                            int pos = tokens[1].indexOf('.'); // find the position of . in file name
                            if (pos != -1) {
                                String extension = tokens[1].substring(pos); // get extension
                                if (extension.equals("gif") ||
                                    extension.equals("jpeg")) {
                                        type = "image/" + extension;
                                    }
                                else { // all other type will be treated as plain text
                                    type = "text/plain";
                                }
                            }
                            // Print Header
                            out.println(StatusLines.CODE200);
                            out.println("Content-type: "+type);
                            out.println("Content-length: "+file.length());
                            out.println(); // end header with blank line
                            out.flush();
                            // Write file
                            WriteFileToSocket(file, clientSocket);
                        }                        
                    }
                }
                ++lineNum;
            } // End while
            // Close output and socket
            out.close();
            clientSocket.close();
        }
        catch (IOException e) {
            e.printStackTrace(); // output exception info
        }
        finally {
            // Debug info
            System.out.format(DEBUGINFO+"Terminated.\n");
        }
    }

    // Obsolete
    // public void WriteFileToOut(String path, PrintWriter out) {
    //     try {
    //         System.out.println(DEBUGINFO+"Writing " + path);
    //         File file = new File(path);
    //         if(!file.exists()) {
    //             out.println(StatusLines.CODE404 + "\r\n");
    //         }
    //         else {
    //             out.println(StatusLines.CODE200);
    //             BufferedReader fread = new BufferedReader(new FileReader(file));
    //             String fline;
    //             while ((fline = fread.readLine()) != null) {
    //                 out.println(fline);
    //             }                                
    //         }
    //     }
    //     catch (Throwable t) {  // catch all here
    //         t.printStackTrace();
    //     }
    // }

    // This function write a file to client socket by using BufferedOutputStream
    public void WriteFileToSocket(File file, Socket socket) {
        try {
            FileInputStream fis = new FileInputStream(file);
            long size = file.length();
            byte[] fdata = new byte[2048];
            BufferedOutputStream dataOut = new BufferedOutputStream(clientSocket.getOutputStream());
            
            int read;
            while ((read = fis.read(fdata)) != -1) {
                dataOut.write(fdata, 0, read);
            }
            dataOut.close();
            fis.close();
        }
        catch (IOException t) {  // catch all here
            t.printStackTrace();
        }
    }
    
}
