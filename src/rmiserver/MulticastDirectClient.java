package rmiserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Scanner;


public class MulticastDirectClient extends Thread {
    private String MULTICAST_ADDRESS = "224.3.2.0";
    private int PORT = 4312;
    private long SLEEP_TIME = 5000;

    public static void main(String[] args) {
        (new MulticastDirectClient()).start();
    }

    public void run() {
        MulticastSocket socket = null;
        System.out.println("Direct client running");
        Scanner sc = new Scanner(System.in);
        try {
            socket = new MulticastSocket();  // create socket without binding it (only for sending)
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            while (true) {
                System.out.print("Send to msg: ");
                String input = sc.nextLine() + "\n";
                DatagramPacket packet = new DatagramPacket(input.getBytes(), input.length(), group, PORT);
                socket.send(packet);
                socket.receive(packet);
                System.out.println(new String(packet.getData()));
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}
