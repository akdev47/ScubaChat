package protocol;


import client.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import ui.MyProtocolListener;

/**
 * This is just some example code to show you how to interact
 * with the server using the provided client and two queues.
 * Feel free to modify this code in any way you like!
 */

public class MyProtocol {

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 4030; //TODO: Set this to your group frequency!
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    // The token you received for your frequency range
    String token = "java-35-5RYH4U071TL8GZ3KOS";

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    private Set<String> forwardedMessages = new HashSet<>();
    private Set<String> recievedMessages = new HashSet<>();
    private TreeSet<Integer> addressesList = new TreeSet<>();

    private List<byte[]> receivedAckPackets = new ArrayList<>();


    private int seqNum = 0;
    private volatile boolean channelFree = true;
    private List<MyProtocolListener> listeners = new ArrayList<>();

    private int clientAddress;

    public TreeSet<Integer> getAddressesList() {
        return addressesList;
    }


    Client client;

    public MyProtocol(String server_ip, int server_port, int frequency) {
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        client = new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue, sendingQueue);

        //        create a random address within 1 byte. do not accept address 0, since this number will
        //         be sent as control information.
        do {
            clientAddress = (int) (Math.random() * 255);
        } while (clientAddress == 0);

        //        clientAddress =  1;

        System.out.println("My address is: " + clientAddress);

    }


    public void startAddressing() {
        //send a data short message with first and second byte 0 to indicate that a new node has
        //joined the network.
        ByteBuffer buff = ByteBuffer.allocate(2);
        buff.put((byte) 0);
        buff.put((byte) 0);

        Message joinMsg = new Message(MessageType.DATA_SHORT, buff);

        try {
            sendingQueue.put(joinMsg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        getAddressesList().add(clientAddress);

        AddressingThread addressingThread = new AddressingThread(addressesList, listeners,sendingQueue);

        // Start the receive thread
        new receiveThread(receivedQueue, addressingThread).start();
        addressingThread.start();

    }

    public void addListener(MyProtocolListener listener) {
        listeners.add(listener);
    }


    public int getClientAddress() {
        return clientAddress;
    }







    public void startSendingThread(String text, int dest) {
        new sendingThread(text,dest).start();
    }


    public class sendingThread extends Thread {

        String messageToSend;
        int destinationAddress;

        public sendingThread(String messageToSend,int destinationAddress) {
            super();
            this.messageToSend = messageToSend;
            this.destinationAddress = destinationAddress;

        }


        @Override
        public void run() {


            //TODO: obviously change this

            //only send you aren't the only node in the network.
            if (addressesList.size() >= 2) {

                System.err.println("THIS NODE SENDING!");

                //destiantionAddress 255 is the address to send for everyone
                if(destinationAddress==255) {
                    for (Integer destAddress : addressesList) {
                        if (destAddress != clientAddress) {
                            boolean destStarted = false;
                            while(!destStarted) {
                                if (channelFree) {
                                    destStarted = true;
                                    sendMethod(destAddress);
                                } else {
                                    System.out.println(
                                            "channel was not free for " + destAddress + "  will try again.");

                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }

                        }

                    }
                }
                else {
                    while (!channelFree) {
                        Thread.onSpinWait();
                    }
                    System.out.println(clientAddress + " going to send to " + destinationAddress);
                    sendMethod(destinationAddress);
                }

            }

            for (MyProtocolListener listener : listeners) {
                listener.onAllACKReceived();
            }
            System.out.println("all acknowledgments done");
            receivedAckPackets.clear();


        }
        public void sendMethod(int destAddress) {

            if (destAddress != clientAddress) {
                System.err.println("NEW ADDRESS STARTED.");

                Packets p = new Packets();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                ackRecievedloop:
                while (!ackReceivedForSeqNum(destAddress)) {

                    try {
                        int delay = (int) (Math.random() * 2501) + 3000;
                        System.out.println("Waiting for " + delay + " milliseconds before sending to destination address");
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    int timeout = 28000 + (int) (Math.random() * 4001);
                    System.out.println("Timeout of: " + timeout + "milliseconds");
                    long startTime = System.currentTimeMillis();


                    byte[] byteArray = p.packetBuilder(clientAddress, destAddress, 8, seqNum, 0, messageToSend.getBytes(), 0);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
                    Message msg = new Message(MessageType.DATA, byteBuffer);

                    try {
                        boolean messageSent = false;
                        System.out.println("Attempting to send packet with seqNum: " + seqNum);

                        while(!messageSent) {

                            if (channelFree) {
                                Thread.sleep((int) (Math.random() * (1001)) + 1050);
                                if (channelFree) {
                                    messageSent= true;
                                    System.out.println("Sent packet with seqNum: " + seqNum);
                                    sendingQueue.put(msg);
                                    Thread.sleep((int) (Math.random() * (401)) + 50);
                                } else {
                                    System.out.println("Could not send packet with seqNum: " + seqNum);
                                }
                            }
                            Thread.sleep((int) (Math.random() * (401)) + 50);
                        }



                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    while (!ackReceivedForSeqNum(destAddress) && System.currentTimeMillis() - startTime < timeout) {

                        if(ackReceivedForSeqNum(destAddress)) {
                            break ackRecievedloop;
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    if (!ackReceivedForSeqNum(destAddress)) {
                        System.out.println("Timeout occurred for packet: " + seqNum + ". Retransmitting...");
                        seqNum++;

                        for(Integer allAddresses : addressesList) {
                            String forwardedMessage = allAddresses+ "-" + destAddress+ "-" + seqNum;
                            if(forwardedMessages.contains(forwardedMessage)) {

                                forwardedMessages.remove(forwardedMessage);
                            }
                        }

                        channelFree = true;

                        if(seqNum>=255) {
                            seqNum = 0;
                            receivedAckPackets.clear();
                            forwardedMessages.clear();
                        }
                    }


                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }


                }

            }
        }

        private boolean ackReceivedForSeqNum(int destAddress) {

            for (byte[] ackPacket : receivedAckPackets) {

                ByteBuffer buffer = ByteBuffer.wrap(ackPacket);

                int packetSeqNum = buffer.get(3) & 0xFF;
                int packetSrcAddress = buffer.get(0) & 0xFF;
                int packetDestAddress = buffer.get(1) & 0xFF;

                if (packetSeqNum == seqNum && clientAddress == packetDestAddress && destAddress == packetSrcAddress) {
                    System.out.println("ACK received for packet: " + seqNum);
                    return true;
                }
            }

            return false;
        }

    }


    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;
        private AddressingThread addressingThread;


        public receiveThread(BlockingQueue<Message> receivedQueue, AddressingThread addressingThread) {
            super();
            this.receivedQueue = receivedQueue;
            this.addressingThread = addressingThread;
        }

        public void run(){
            while(true) {
                try{
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.BUSY){
                        System.out.println("BUSY");
                        channelFree = false;
                    } else if (m.getType() == MessageType.FREE){
                        System.out.println("FREE");
                        channelFree = true;
                    }else if (m.getType() == MessageType.DATA) {
                        channelFree = false;

                        int sourceAddress = m.getData().get(0) & 0xFF;
                        int destAddress =m.getData().get(1) & 0xFF;
                        int ttl = m.getData().get(2) & 0xFF;
                        int seqNum = m.getData().get(3) & 0xFF;
                        int flag = m.getData().get(4) & 0xFF;
                        int ack = m.getData().get(6) & 0xFF;


                        if(sourceAddress!= clientAddress && destAddress== clientAddress && ack==1) {
                            System.out.println("Ack recieved to my address: " + sourceAddress + " to " + destAddress);
                            byte[] ackPacket = m.getData().array();

                            boolean isDuplicate = false;
                            for (byte[] packet : receivedAckPackets) {
                                if (Arrays.equals(packet, ackPacket)) {
                                    isDuplicate = true;
                                    break;
                                }
                            }

                            if (!isDuplicate) {
                                System.out.println("Added Ack packet to recieved ack packets.");
                                receivedAckPackets.add(ackPacket);
                            }
                        }
                        else if(sourceAddress!= clientAddress && destAddress != clientAddress && ack==1) {
                            //

                            String forwardedMessage = sourceAddress + "-" + destAddress + "-" + seqNum;
                            if (!forwardedMessages.contains(forwardedMessage)) {

                                try {

                                    byte[] byteArray = m.getData().array();

                                    int updatedTTL = (m.getData().get(2) & 0xFF) - 1;

                                    if (updatedTTL >= 0) {


                                        boolean messageSent = false;
                                        System.out.println(
                                                "attempting to forward ack recieved  from: " + sourceAddress + " to " + destAddress + " (i am address: " + clientAddress + ")");
                                        int randomDelay = (int) (Math.random() * 4001) + 1000;
                                        System.out.println("waiting for " + randomDelay + " seconds " + " before transmission.");
                                        Thread.sleep(randomDelay);

                                        int numberOfAttempts = 0;
                                        while (!messageSent) {

                                            if (channelFree) {
                                                Thread.sleep(500);
                                                if (channelFree) {
                                                    messageSent = true;

                                                    byteArray[2] = (byte) updatedTTL;

                                                    ByteBuffer modifiedByteBuffer = ByteBuffer.wrap(
                                                            byteArray);

                                                    Message newMsg = new Message(MessageType.DATA,
                                                                                 modifiedByteBuffer);
                                                    forwardedMessages.add(forwardedMessage);
                                                    sendingQueue.put(newMsg);

                                                    System.out.println( "Forwarded ack recieved  from: " + sourceAddress + " to " + destAddress + " (i am address: " + clientAddress + ")");


                                                }
                                            } else {
                                                Message mType = receivedQueue.take();

                                                if(mType.getType() == MessageType.FREE) {
                                                    channelFree = true;
                                                }

                                                System.err.println("channel busy.");
                                                numberOfAttempts++;

                                                if (numberOfAttempts > 100) {
                                                    System.err.println("channel busy for too long. freeing channel");
                                                    channelFree = true;
                                                }
                                                Thread.sleep((int) (Math.random() * (1401)) + 400);
                                            }
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }

                            }



                        }
                        else if((destAddress) == clientAddress) {
                            int length = m.getData().get(5) & 0xFF;
                            String messageThatsRecieved = "";
                            for (int i = 0; i < length ; i++){
                                char c = (char) m.getData().get(7+i);
                                messageThatsRecieved += c;
                            }

                            String recievedMessage = sourceAddress + "-" + destAddress + "-" + messageThatsRecieved;
                            int randomD = (int) (Math.random() * 4001) + 4000;
                            System.out.println("Message received: Attempting to send Ack to address" + sourceAddress + " after " +randomD + " ms");
                            Thread.sleep(randomD);


                            if(!recievedMessages.contains(recievedMessage)) {
                                recievedMessages.add(recievedMessage);
                                for (MyProtocolListener listener : listeners) {
                                    System.out.println("entered listener");
                                    listener.onMessageReceieved(messageThatsRecieved,sourceAddress);
                                }
                            }


                            boolean messageSent = false;
                            int busyCount = 0;

                            while (!messageSent) {

                                if(channelFree) {
                                    Thread.sleep((int) (Math.random() * (401)) + 50);
                                    if(channelFree) {
                                        messageSent= true;
                                        System.out.println("Sending recieved Ack to " + messageThatsRecieved);
                                        Packets p = new Packets();
                                        byte[] byteArray = p.packetBuilder(clientAddress, sourceAddress, 2, seqNum, 0,
                                                                           messageThatsRecieved.getBytes(),1);
                                        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
                                        Message msg = new Message(MessageType.DATA, byteBuffer);


                                        sendingQueue.put(msg);
                                        Thread.sleep(100);
                                    }else {
                                        System.out.println("Could not send ack to" + messageThatsRecieved + "will try again");
                                    }
                                } else {
                                    Message mType = receivedQueue.take();

                                    if(mType.getType() == MessageType.FREE) {
                                        channelFree = true;
                                    }
                                    System.err.println("Channel busy rn");
                                    busyCount++;

                                    if (busyCount >= 50) {
                                        System.out.println("Channel has been busy for too long, resetting channel to free.");
                                        channelFree = true;
                                        busyCount = 0;
                                    }

                                }
                                Thread.sleep((int) (Math.random() * (401)) + 1000);
                            }

                        } else if (sourceAddress != clientAddress) {
                            System.out.println("current packet src: " + sourceAddress);
                            System.out.println("current packet dest: " + destAddress);
                            System.out.println("my node address " + clientAddress);

                            String forwardedMessage = sourceAddress + "-" + destAddress + "-" + seqNum;

                            if (!forwardedMessages.contains(forwardedMessage)) {
                                try {
                                    byte[] byteArray = m.getData().array();
                                    int updatedTTL = (m.getData().get(2) & 0xFF) - 1;

                                    if (updatedTTL >= 0) {
                                        boolean messageSent = false;
                                        int numberOfAttempts = 0;
                                        System.out.println("Attempting to forward and subtract TTL received  from: " + sourceAddress + " to " + destAddress + " (i am address: " + clientAddress + ")");
                                        int randomDelay = (int) (Math.random() * 6001) + 1000;
                                        System.out.println("waiting for " + randomDelay + " seconds " + " before transmission.");

                                        while (!messageSent) {
                                            if (channelFree) {
                                                if (channelFree) {
                                                    messageSent = true;
                                                    byteArray[2] = (byte) updatedTTL;
                                                    ByteBuffer modifiedByteBuffer = ByteBuffer.wrap(byteArray);
                                                    Message newMsg = new Message(MessageType.DATA, modifiedByteBuffer);
                                                    sendingQueue.put(newMsg);
                                                    forwardedMessages.add(forwardedMessage);
                                                    System.err.println("Forwarded and subtracted TTL ");



                                                }
                                            } else {

                                                Message mType = receivedQueue.take();

                                                if(mType.getType() == MessageType.FREE) {
                                                    channelFree = true;
                                                }

                                                System.err.println("channel busy.");
                                                numberOfAttempts++;

                                                if (numberOfAttempts > 50) {
                                                    System.err.println("channel busy for too long. freeing channel");
                                                    channelFree = true;
                                                }
                                                Thread.sleep((int) (Math.random() * (1401)) + 1000);
                                            }
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }


                    }else if (m.getType() == MessageType.DATA_SHORT) {
                        channelFree = false;
                        System.out.println("Short data received");
                        ByteBuffer data = m.getData();
                        data.position(0);
                        byte controlByte = data.get(0);
                        if (controlByte == 0x00) {

                            byte addressByte = data.get(1);

                            if((addressByte & 0xFF) != 0) {

                                int addressInt1 = addressByte & 0xFF;
                                System.out.println("Received address: " + addressInt1);
                                if (getAddressesList().contains(addressInt1)) {
                                    System.out.println("This address is already contained!");
                                } else {
                                    System.out.println("Added to clients address list.");

                                    getAddressesList().add(addressInt1);


                                }
                            }
                            else {
                                System.out.println("NEW NODE JOINED.");

                                ByteBuffer buff = ByteBuffer.allocate(2);
                                buff.put((byte) 0);
                                buff.put((byte) 0);

                                Message joinMsg = new Message(MessageType.DATA_SHORT, buff);

                                try {
                                    sendingQueue.put(joinMsg);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                if (!addressingThread.isRunning()) {
                                    System.out.println("AddressingThread is not running, starting it now..");
                                    addressingThread.setRunning(true);
                                    new AddressingThread(addressesList, listeners,sendingQueue).start();
                                } else {
                                    System.out.println("AddressingThread is already running!!");
                                }
                            }

                            int randomDelay = (int) (Math.random() * 401) + 300;


                            Thread.sleep(randomDelay);
                        }
                    }else if (m.getType() == MessageType.DONE_SENDING){
                        channelFree = true;
                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO){
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING){
                        channelFree = false;
                        System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END){
                        System.out.println("END");
                        System.exit(0);
                    } else if (m.getType() == MessageType.TOKEN_ACCEPTED){
                        System.out.println("Token Valid!");
                    } else if (m.getType() == MessageType.TOKEN_REJECTED){
                        System.out.println("Token Rejected!");
                    }
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }
            }
        }

    }

    public boolean containsInAddressList(int address) {

        for(Integer addressInList : addressesList) {
            if(addressInList == address) {
                return true;
            }
        }
        return false;
    }


}



