package ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TreeSet;
import javax.swing.*;
import java.awt.*;
import protocol.MyProtocol;


public class ScubaChatGUI implements MyProtocolListener {

    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 4030;

    private JFrame frame;
    private JLabel titleLabel;
    private JButton startButton;
    private JPanel chatPanel;
    private JTextArea receivedMessages;
    private JTextArea typingArea;
    private JButton sendButton;
    private JLabel myAddressLabel;
    private JLabel othersAddressLabel;
    private JLabel infoLabel;



    private MyProtocol myProtocol;

    public ScubaChatGUI() {
        initializeGUI();
        startProtocolInBackground();


    }

    private void initializeGUI() {
        frame = new JFrame("ScubaChat");
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(null);

        titleLabel = new JLabel("Welcome to ScubaChat");
        titleLabel.setFont(new Font("Comfortaa", Font.ITALIC, 24));
        titleLabel.setBounds(120, 50, 300, 50);
        frame.add(titleLabel);

        startButton = new JButton("Start");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startButton.setText("Waiting For All The Addresses...");

                final MyProtocol protocol = myProtocol;

                new Thread(() -> {
                    synchronized (this) {
                        if (protocol != null) {
                            protocol.startAddressing();
                        } else {
                            System.out.println("NULL PROTOCOL");
                        }
                    }
                }).start();

            }
        });
        startButton.setBounds(200, 120, 100, 50);
        frame.add(startButton);


        chatPanel = new JPanel(null);
        chatPanel.setBounds(0, 0, 500, 500);
        chatPanel.setBackground(new Color(33, 84, 179));
        chatPanel.setVisible(false);

        receivedMessages = new JTextArea();
        receivedMessages.setEditable(false);
        receivedMessages.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(receivedMessages);
        scrollPane.setBounds(50, 50, 400, 200);
        receivedMessages.setVisible(true);
        chatPanel.add(scrollPane);

        typingArea = new JTextArea();
        typingArea.setLineWrap(true);
        JScrollPane typingScrollPane = new JScrollPane(typingArea);
        typingScrollPane.setBounds(50, 270, 300, 100);
        chatPanel.add(typingScrollPane);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> {


            String message = typingArea.getText();


            if (message != null && message.contains("~")) {

                String[] parts = message.split("~");
                if (parts.length == 2) {
                    String text = parts[0];
                    int destinationAddress = Integer.parseInt(parts[1]);

                    if (destinationAddress >= 0 && destinationAddress <= 255 && myProtocol.containsInAddressList(destinationAddress)) {

                        if(text.length()>=26) {
                            infoLabel.setText("Please enter a shorter message.");
                            infoLabel.setForeground(Color.RED);
                        }
                        else {
                            sendButton.setEnabled(false);
                            sendButton.setText(" ");
                            sendButton.setBackground(new Color(145, 26, 0));
                            myProtocol.startSendingThread(text, destinationAddress);
                            System.out.println("Sending: " + text);

                            if(destinationAddress!=255) {
                                infoLabel.setText("Message sent to: " + destinationAddress);
                                infoLabel.setForeground(new Color(100, 255, 52));
                            } else {
                                infoLabel.setText("SENDING BROADCAST." );
                                infoLabel.setForeground(new Color(255, 138, 56));
                            }
                        }


                    } else {
                        infoLabel.setText("Invalid destination address.");
                        infoLabel.setForeground(Color.RED);
                    }
                } else {
                    infoLabel.setText("Wrong format. Please use 'message~destination'");
                    infoLabel.setForeground(Color.RED);
                }
            } else {
                infoLabel.setText("Do not enter a empty message.");
                infoLabel.setForeground(Color.RED);
            }
        });
        sendButton.setBounds(360, 270, 90, 100);
        sendButton.setVisible(false);
        chatPanel.add(sendButton);

        myAddressLabel = new JLabel("My ADDRESS: ");
        myAddressLabel.setBounds(60,340,200,100);
        myAddressLabel.setForeground(Color.WHITE);
        myAddressLabel.setVisible(false);
        frame.add(myAddressLabel);

        infoLabel = new JLabel("Please use this message format; message~destination,(255 for brdcst) ");
        infoLabel.setForeground(new Color(214, 255, 177));
        infoLabel.setBounds(40,410,500,100);
        infoLabel.setVisible(false);
        frame.add(infoLabel);

        othersAddressLabel = new JLabel("ALL ADDRESSES: ");
        othersAddressLabel .setBounds(60,360,300,100);
        othersAddressLabel.setForeground(Color.WHITE);
        othersAddressLabel.setVisible(false);
        frame.add(othersAddressLabel);

        frame.add(chatPanel);
        frame.setVisible(true);
    }



    public void closeStartScreen() {
        chatPanel.setVisible(true);

        titleLabel.setVisible(false);
        startButton.setVisible(false);
        sendButton.setVisible(true);

        myAddressLabel.setVisible(true);
        infoLabel.setVisible(true);
        othersAddressLabel.setVisible(true);
    }

    private void startProtocolInBackground() {

        new Thread(() -> {
            MyProtocol protocol = new MyProtocol(SERVER_IP, SERVER_PORT, frequency);
            protocol.addListener(ScubaChatGUI.this);
            myProtocol = protocol;
            System.out.println("Protocol initialized...");
        }).start();
    }

    public static void main(String[] args) {
        new ScubaChatGUI();

    }

    @Override
    public void onAddressingCompleted() {
        System.out.println("addressing completed");
        SwingUtilities.invokeLater(() -> {
            closeStartScreen();
            int myAddress = myProtocol.getClientAddress();
            myAddressLabel.setText("My ADDRESS: " + myAddress);

            TreeSet<Integer> allAddresses = myProtocol.getAddressesList();
            StringBuilder stringBuilder = new StringBuilder("ALL ADDRESSES: ");

            for(Integer address : allAddresses) {
                stringBuilder.append(address).append(" ");
            }
            othersAddressLabel.setText(stringBuilder.toString());

        });
    }

    @Override
    public void onMessageReceieved(String message, int source) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("message received");
            String currentText = receivedMessages.getText();


            String newText =source+": " +  message + "\n" + currentText;


            receivedMessages.setText(newText);

            receivedMessages.setCaretPosition(0);
            infoLabel.setText("Message Successfully Recieved: " + message);
            infoLabel.setForeground(new Color(221, 255, 44));
        });
    }

    @Override
    public void onAllACKReceived() {
        SwingUtilities.invokeLater(() -> {

            sendButton.setEnabled(true);
            sendButton.setText("SEND");
            sendButton.setBackground(Color.WHITE);

            infoLabel.setForeground(new Color(255, 103, 103));
            infoLabel.setText("CLEAR TO SEND: ");

        });
    }
}