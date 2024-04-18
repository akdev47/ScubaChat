package ui;

public interface MyProtocolListener {


    void onAddressingCompleted();

    void onMessageReceieved(String text, int sourceAddress);

    void onAllACKReceived();
}
