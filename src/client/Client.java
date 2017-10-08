package client;

import ui.PlaceholderTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Created by Codrin on 9/29/2017.
 */
public class Client extends JFrame {

    private static final int PORT = 936;

    public static final int ACTIVE_INTERVAL = 60;
    public static final int ACTIVE_DELAY = 10;

    private JList msgList;
    private JList userList;
    private PlaceholderTextField message;
    private JButton sendButton;
    private DefaultListModel<String> msgListModel = new DefaultListModel<>();
    private DefaultListModel<String> usersListModel = new DefaultListModel<>();

    private static final String MESSAGE_PLACEHOLDER = "Type here...(type \"/quit\" to Quit)";

    private String currentUsername = null;

    public static void main(String[] args) {
        new Client();
    }

    public Client() {
        super();

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        msgList = new JList(msgListModel);
        userList = new JList(usersListModel);
        message = new PlaceholderTextField();
        message.setPlaceholder(MESSAGE_PLACEHOLDER);
        sendButton = new JButton("Send");
        message.setEnabled(false);
        sendButton.setEnabled(false);

        this.setTitle("Welcome Codrin-sama  ");
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        Container contentPane = this.getContentPane();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Message log
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.7;
        gbc.weighty = 0.9;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(2,2,10,6);
        contentPane.add(msgList, gbc);

        // User list
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.3;
        gbc.weighty = 0.9;
        gbc.gridx = 1;
        gbc.gridy = 0;
        contentPane.add(userList, gbc);

        // Chat input
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.7;
        gbc.weighty = 0.1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.insets = new Insets(2,2,10,6);
        contentPane.add(message, gbc);

        // Send
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.3;
        gbc.weighty = 0.1;
        gbc.gridx = 1;
        gbc.gridy = 1;
        contentPane.add(sendButton, gbc);

        this.setBounds(200, 200, 600, 500);
        this.setVisible(true);

        Socket socket = null;
        boolean failed = true;
        while (failed) {
            failed = false;
            try {
                String[] parts = this.launchServerConnectionDialog().split(":");
                socket = new Socket(parts[0], Integer.parseInt(parts[1]));
            } catch (Exception e) {
                e.printStackTrace();
                failed = true;
            }
        }
        if (socket != null) {
            try {
                this.run(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String launchServerConnectionDialog() {

        JPanel jPanel = new JPanel(new BorderLayout(5,5));
        JPanel label = new JPanel(new GridLayout(0,1,2,2));
        label.add(new JLabel("IP", SwingConstants.RIGHT));
        label.add(new JLabel("PORT", SwingConstants.RIGHT));
        jPanel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0,1,2,2));
        JTextField ipTextField = new JTextField();
        controls.add(ipTextField);
        JTextField portTextField = new JTextField();
        controls.add(portTextField);
        jPanel.add(controls, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, jPanel,"Connection", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.CANCEL_OPTION) {
            System.exit(0);
        }
        return ipTextField.getText() + ":" + portTextField.getText();
    }

    public String launchUsernameDialog(String error) {

        JPanel jPanel = new JPanel(new BorderLayout(5,5));
        jPanel.add(new JLabel(error), BorderLayout.NORTH);
        jPanel.add(new JLabel("Username: ", SwingConstants.RIGHT), BorderLayout.WEST);

        JTextField ipTextField = new JTextField();
        jPanel.add(ipTextField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, jPanel,"Connection", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.CANCEL_OPTION) {
            System.exit(0);
        }
        return ipTextField.getText();
    }

    public void run(Socket socket) throws IOException{

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                out.println("QUIT");
                System.exit(0);
            }
        });

        // 1. Prepare Send action handler
        sendButton.addActionListener((event) -> {
            if (message.getText().toLowerCase().equals("/quit")) {
                out.println("QUIT");
                System.exit(0);
            } else {
                this.sentMessage(out);
            }
        });

        // Enter press action
        message.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode()==KeyEvent.VK_ENTER) {
                    if (message.getText().toLowerCase().equals("/quit")) {
                        out.println("QUIT");
                        System.exit(0);
                    } else {
                        sentMessage(out);
                    }
                }
            }
        });


        // 2. INIT protocol

        this.setUsername(this.launchUsernameDialog(null));
        out.println("JOIN " + this.getUsername());


        while (true) {
            String msg = in.readLine();
            if (msg != null && !msg.isEmpty()) {
                System.out.println(" --> " + msg);
                switch (msg.substring(0, 4)) {
                    case "J_OK":
                        //TODO: enable msg input and "send" button
                        message.setEnabled(true);
                        sendButton.setEnabled(true);
                        System.out.println("OK");
                        this.startActiveLoop(out);
                        break;
                    case "J_ER":
                        String payload = msg.substring(5, msg.length());
                        payload = payload.trim();
                        String[] parts = payload.split(":");
                        System.out.println("Error (" + parts[0] + ") : " + parts[1]);
                        // shows the username dialog
                        this.setUsername(this.launchUsernameDialog(parts[1]));
                        out.println("JOIN " + this.getUsername());

                        break;
                    case "DATA":
                        String dataPayload = msg.substring(5, msg.length());
                        dataPayload = dataPayload.trim();
                        String[] dataParts = dataPayload.split(":");
                        System.out.println("Message receive from (" + dataParts[0] + ") : " + dataParts[1]);
                        msgListModel.addElement(dataParts[0] + ": " + dataParts[1]);
                        break;
                    case "LIST":
                        String listPayload = msg.substring(5, msg.length());
                        listPayload = listPayload.trim();
                        String[] listParts = listPayload.split("\\s+");
                        System.out.println("Server online user-list : " + listParts);
                        usersListModel.clear();
                        for (int i = 0; i < listParts.length; i++) usersListModel.addElement(listParts[i]);
                        break;
                    default:
                        System.out.println("Invalid message: " + msg);
                        break;
                }
            }
        }
    }

    private String getUsername() {
        return currentUsername;
    }

    private void setUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    private void sentMessage (PrintWriter out) {
        String inputMsg = message.getText();
        if (!inputMsg.isEmpty() && inputMsg.length() <= 250) {
            out.println("DATA " + this.getUsername() + ":" + inputMsg);
            message.setText(null);
        }
    }

    private void startActiveLoop(PrintWriter out) {
        Timer timer = new Timer(ACTIVE_INTERVAL * 1000, (event) -> {
            out.println("IMAV");
        });
        timer.setInitialDelay(0);
        timer.start();
    }
}
