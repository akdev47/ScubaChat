package protocol;

import client.Message;
import client.MessageType;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import ui.MyProtocolListener;


public class AddressingThread extends Thread {
    private volatile boolean running = true;
    private final TreeSet<Integer> addressesList;
    private final List<MyProtocolListener> listeners;
    private final BlockingQueue<Message> sendingQueue;
    private int addressingTime = 40000;


    public AddressingThread(TreeSet<Integer> addresses, List<MyProtocolListener> listeners, BlockingQueue<Message> sendingQueue) {
        super();
        this.addressesList = addresses;
        this.listeners = listeners;
        this.sendingQueue = sendingQueue;

    }

    @Override
    public void run() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                running = false;
                System.out.println("thread stopped after " +(addressingTime/1000) + " secs");

                for (MyProtocolListener listener : listeners) {
                    System.out.println("entered listener");
                    listener.onAddressingCompleted();
                }



            }
        }, addressingTime);

        while (running) {
            //wait a random number of seconds for every node.
            try {
                Thread.sleep((int) (Math.random() * 3001) + 1500);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("entered loop");

            //send each address in the address list of the node to all other nodes in transmission
            //range.


            for (Integer address : addressesList) {
                try {
                    //first byte is control info, second byte is the address.
                    ByteBuffer buff2 = ByteBuffer.allocate(2);
                    buff2.put((byte) 0);
                    buff2.put((byte) (address & 0xFF));

                    Message msg = new Message(MessageType.DATA_SHORT, buff2);

                    //add to sending queue if it isnt contained.
                    if (!sendingQueue.contains(msg)) {
                        System.out.println("added " + address + " to sending queue.");
                        sendingQueue.put(msg);
                    }
                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
            }
        }
        timer.cancel();
    }
    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

}