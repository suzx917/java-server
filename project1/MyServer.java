/*
Author: Zixiu Su
ID: 1001820076
Date: 9/28/2020

This is a simple web server handling HTTP request with TCP connection,

This serve is implemented as thread-per-connection. The main thread will start a socket
at designated port number and then a connection thread for each connection as long as 
total active connection is below the limit amount.
Each connection thread handles a TCP connection. Status of threads (active/idle) will
be monitored by a ConcurrentHashMap data structure (to ensure thread safety).
Since no specification regarding task management, after reaching the limit number of
active connections, new connection request will be DROPPED.

It only responds to properly formatted GET request with code 200 301 or 404, only an
index page will be served. See ConnectionHandler.run() for parsing logic.

Constants such as port number, max number of connections, etc, are specified in the 
members of the MyServer class.

Java socket and thread usage learned from these text books:
Computer Networking: A Top-Down Approach (5th edition) - Kurose and Ross
Learning Network Programming with Java - Reese
*/

import java.io.*;
import java.util.stream.*;
import java.util.concurrent.*;
import java.net.*;

// 
// static class ServerResponses {
//     public static final String Code200 = ""
// }

class ConnectionHandler implements Runnable {
    // Handler id corresponds to its index in the hashmap
    private static int id;
    // Saving concurrent hashmap here, reset thread flag when terminate 
    private static ConcurrentHashMap<Integer,Integer> map;
    // 
    private static Socket clientSocket;

    public ConnectionHandler(int _id, ConcurrentHashMap<Integer,Integer> _m, Socket _s) {
        id = _id;
        map = _m;
        clientSocket = _s;
    }

    // Implement Java Runnable interface with thread task function
    // Parse incoming HTTP request headers and send back proper responses
    @Override
    public void run() {
        // Reading received request from client socket
        try(BufferedReader inBuffer = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream())))
        {
            int lineNum = 1;
            String line;
            while (lineNum < 5 && (line = inBuffer.readLine()) != null) {
                //
                // Parse HTTP header
                //
                System.out.println(line);
                if (lineNum == 1) {
                    String[] tokens = line.split(" ");
                    // If not a GET Request discard it 
                    if (tokens.length != 3 || tokens[0].equals("GET") == false) {
                        System.out.println("Invalid Request.");
                        break;
                    }
                    // else if (tokens[i])
                }
                ++lineNum;
            }
            clientSocket.close();
        }
        catch (IOException e) {
            e.printStackTrace(); // output exception info
        }
            
        // Reset connection/thread status before terminate
        map.compute(id, (key,val) -> 0);
        System.out.format("Connection #%d Terminated: %d\n", id, map.get(id));
    }
}

public class MyServer{

    ///////////////////// Server Settings /////////////////////

    // Server Info
    static final String SERVER_NAME = "localhost";
    static final int PORT_NUMBER = 6789;

    // Max number of connection threads
    static final int MAX_CONNECTIONS = 64;

    ///////////////////////////////////////////////////////////

    // Each thread will have an index in [0, MAX_CONNECTIONS)
    // This array will keep track which index is active
    // When task thread #i is generated, bitmap[i] is set to 1
    // When task thread ends it will flip bitmap[i] back to 0 (no race condition here)
    // If all connection are active the request will be dropped
    protected static ConcurrentHashMap<Integer,Integer> connectionMap;

    public static void main(String[] args) {
        // Initialize connection/thread map
        connectionMap = new ConcurrentHashMap<Integer,Integer>();
        for (int i = 0; i < MAX_CONNECTIONS; i++) {
            connectionMap.put(i,0);
        }
        
        //
        try {
            ServerSocket welcomeSocket = new ServerSocket(PORT_NUMBER);
            System.out.println("Server started.");

            int test = 100;
            // Main loop
            while (test > 0) {
                // Try to find the first idle thread slot from map
                int idle = -1;
                for (int i = 0; i < MAX_CONNECTIONS; i++) {
                    if (connectionMap.get(i) == 0)
                        idle = i;
                        break;
                }

                if (idle == -1) // No idle threads, go next loop
                    continue;
                
                // Start a new socket and a corresponding thread
                connectionMap.compute(idle, (key,val) -> 1); // set hashmap flag first
                try {
                    Socket connectionSocket = welcomeSocket.accept();
                    new Thread(new ConnectionHandler(idle, connectionMap, connectionSocket)).start();
                }
                catch (IOException e) {
                    connectionMap.compute(idle, (key,val) -> 0); // reset bitmap flag
                    e.printStackTrace(); // output exception info
                }

                // Update connection info to terminal
                Integer activeNumber = connectionMap.values().
                                    stream().
                                    mapToInt(Integer::valueOf).
                                    sum();
                System.out.format("Active Connections: %d/%d\n", activeNumber,
                                MAX_CONNECTIONS);
                test--;                
            }// End main loop
        } 
        catch (IOException e) {
            System.out.println("Failed to get server socket.");
            e.printStackTrace(); // output exception info
        }
    }
}
