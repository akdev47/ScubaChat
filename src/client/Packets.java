package client;

import java.nio.ByteBuffer;

public class Packets {

    public byte[] packetBuilder(int sourceIP, int destIP, int ttl, int seqNum, int flag, byte[] data, int ack) {


        byte[] packet = new byte[32];

        packet[0] = (byte) sourceIP;
        packet[1] = (byte) destIP;
        packet[2] = (byte) ttl;
        packet[3] = (byte) seqNum;
        packet[4] = (byte) flag;
        packet[5] = (byte) data.length;
        packet[6] = (byte) ack;

        System.arraycopy(data, 0, packet, 7, data.length);

        return packet;


    }
}