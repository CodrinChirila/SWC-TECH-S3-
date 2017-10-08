package server;

import client.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Codrin on 9/29/2017.
 */
public class Server {

    private static final int PORT = 936;

    private static final int CHECK_ACTIVE_INTERVAL = 93; // seconds;

    private static final String USERNAME_TOO_LONG = "001";
    private static final String USERNAME_TOO_LONG_MSG = "Username too long(<= 12 char)";
    private static final String INVALID_USERNAME_CHARACTER = "002";
    private static final String INVALID_USERNAME_CHARACTER_MSG = "Invalid username (wrong char)";
    private static final String USERNAME_TAKEN = "003";
    private static final String USERNAME_TAKEN_MSG = "Username taken";

    protected static HashMap<String, HashMap<String, Object>> users = new HashMap<>();


    public static void main(String[] args) throws Exception {
        ServerSocket socket = new ServerSocket(PORT);
        try {
            System.out.println("Server running");
            kickUsers();
            while (true) {
                new Handler(socket.accept()).start();
            }
        } finally {
            socket.close();
        }

    }

    private static class Handler extends Thread {

        Socket socket;
        BufferedReader in;
        PrintWriter out;
        String username;


        public Handler (Socket socket) {
            this.socket = socket;
            System.out.println("New connection");
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out =new PrintWriter(socket.getOutputStream(),true);


                while (true && !socket.isClosed()) {
                    String msg = in.readLine();
                    System.out.println(" --> " + msg);

                    switch (msg.substring(0, 4)) {
                        case "JOIN":
                            String username = msg.substring(5, msg.length());
                            username = username.trim();
                            if (username.length() <= 12) {
                                if (username.matches("^[a-zA-Z0-9_-]*$")) {
                                    synchronized (users) {
                                        System.out.println("Test");
                                        if (users.get(username) == null) {
                                            out.println("J_OK");
                                            this.username = username;
                                            HashMap<String, Object> content = new HashMap<>();
                                            content.put("socket", socket);
                                            content.put("writer", out);
                                            content.put("timestamp",  System.currentTimeMillis());
                                            users.put(username, content);
                                            sendUserList();
                                        } else {
                                            out.print("J_ERR " + USERNAME_TAKEN + ":" + USERNAME_TAKEN_MSG);
                                            System.out.println("ok2");
                                        }
                                    }
                                } else {
                                    out.println("J_ERR " +INVALID_USERNAME_CHARACTER + ":" + INVALID_USERNAME_CHARACTER_MSG);
                                }
                            } else {
                                out.println("J_ERR" + USERNAME_TOO_LONG + ":" + USERNAME_TOO_LONG_MSG);
                            }
                            break;
                        case "DATA":
                            synchronized (users) {
                                for(Map.Entry<String, HashMap<String, Object>> entry: users.entrySet()) {
                                    PrintWriter tmpWriter = (PrintWriter) entry.getValue().get("writer");
                                    tmpWriter.println(msg);
                                }
                            }
                            break;
                        case "IMAV":
                            System.out.println(this.username);
                            synchronized (users) {
                                HashMap<String, Object> content = users.get(this.username);
                                content.put("timestamp",  System.currentTimeMillis());
                                users.replace(this.username, content);
                            }
                            break;
                        case "QUIT":
                            synchronized (users) {
                                users.remove(this.username);
                                sendUserList();
                            }
                            out.println(this.username + "left the chat");
                            socket.close();
                            break;
                        default:
                            System.out.println("Invalid message: " + msg + " " + msg.substring(0, 3));
                            break;
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (users) {
                    users.remove(this.username);
                }
            }
        }
    }

    protected static void sendUserList() {
        synchronized (users) {
            String result = "LIST ";
            for(Map.Entry<String, HashMap<String, Object>> entry: users.entrySet()) {
                result += entry.getKey() + " ";
            }

            for(Map.Entry<String, HashMap<String, Object>> entry: users.entrySet()) {
                PrintWriter tmpWriter = (PrintWriter) entry.getValue().get("writer");
                tmpWriter.println(result);
            }

        }
    }

    protected static void kickUsers() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (users) {
                    for(Map.Entry<String, HashMap<String, Object>> entry: users.entrySet()) {
                        long lastActiveTime = (long) entry.getValue().get("timestamp");
                        System.out.println("if " + lastActiveTime + " "+ System.currentTimeMillis()+ " ---"+ 1000 * (Client.ACTIVE_INTERVAL + Client.ACTIVE_DELAY));
                        if (lastActiveTime - System.currentTimeMillis() >= 1000 * (Client.ACTIVE_INTERVAL + Client.ACTIVE_DELAY)) {
                            Socket tmpSocket = (Socket) entry.getValue().get("socket");
                            System.out.println("Kick user:" + entry.getKey());
                            sendUserList();
                            try {
                                tmpSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }, 0, CHECK_ACTIVE_INTERVAL * 1000);
    }
}
