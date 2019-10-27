import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RMIServer extends UnicastRemoteObject implements IServer {

    static HashMap<Integer, HashMap<String, String>> waitList = new HashMap<>();
    private static String MULTICAST_ADDRESS = "224.3.2.0";
    static HashMap<String, IClient> loggedUsers = new HashMap<>(); // TODO change to syncronized hashmap ?
    int TIMEOUT_TIME = 1000;
    MulticastSocket socket;
    InetAddress group;
    AtomicInteger reqId = new AtomicInteger(0);
    HashMap<String, String> receivedData = null;
    private int PORT = 4312;

    public RMIServer(MulticastSocket socket, InetAddress address) throws RemoteException {
        super();
        System.out.println("RMI server waiting to receive remote calls");
        this.socket = socket;
        this.group = address;
    }

    public static void main(String[] args) {
        System.getProperties().put("java.security.policy", "policy.all");
        IServer server = null;
        try {
            MulticastSocket socket = new MulticastSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            server = new RMIServer(socket, group);
            if (args.length == 1)
                LocateRegistry.getRegistry(7000).rebind("RMIserver" + args[0], server);
            else
                LocateRegistry.getRegistry(7000).rebind("RMIserver", server);
            (new Receiver(socket)).start();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public String sayHello() {
        return "Hello from RMI server";
    }

    @Override
    public void subscribe(String name, IClient client) throws RemoteException {

    }

    @Override
    public PacketBuilder.RESULT register(IClient client, String name, String password) throws RemoteException {
        int packetReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.RegisterPacket(packetReqId, name, password);
        sendPacket(packet, packetReqId);
        //client.printMessage(this.receivedData.get("MSG")); //WTF TODO this
        PacketBuilder.RESULT result = PacketBuilder.RESULT.valueOf(this.receivedData.get("RESULT"));
        return result;
    }

    @Override
    public PacketBuilder.RESULT login(IClient client, String name, String password) throws RemoteException {
        int packetReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.LoginPacket(packetReqId, name, password);
        sendPacket(packet, packetReqId);
        PacketBuilder.RESULT result = PacketBuilder.RESULT.valueOf(this.receivedData.get("RESULT"));
        if (Boolean.parseBoolean(receivedData.get("ADMIN"))) {
            client.setAdmin();
        }
        synchronized (loggedUsers) {
            loggedUsers.put(name, client);
        }
        return result;
    }

    @Override
    public PacketBuilder.RESULT indexRequest(String url) throws RemoteException {
        int packerReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.IndexPacket(packerReqId, url);
        sendPacket(packet, packerReqId);
        return PacketBuilder.RESULT.valueOf(receivedData.get("RESULT"));
    }

    @Override
    public String[] search(IClient client, String[] words, String user) throws RemoteException {
        int packetReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.SearchPacket(packetReqId, words, user);
        sendPacket(packet, packetReqId);
        client.printMessage("Seach complete");
        String[] result = new String[10];
        for (int i = 0; i < Integer.parseInt(receivedData.get("PAGE_COUNT")); i++) {
            String url = receivedData.get("URL_" + i);
            String name = receivedData.get("NAME_" + i);
            String desc = receivedData.get("DESC_" + i);
            result[i] = url + name + "\n" + desc;
        }
        return result;

    }

    @Override
    public ArrayList<String> getUserHistory(IClient client, String username) throws RemoteException {
        int packetReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.RequestHistoryPacket(packetReqId, username);
        sendPacket(packet, packetReqId);
        ArrayList<String> history = new ArrayList<>();
        for (int i = 0; i < Integer.parseInt(receivedData.get("HIST_COUNT")); i++) {
            String date = receivedData.get("DATE_" + i);
            String query = receivedData.get("QUERY_" + i);
            history.add(date + " | " + query);
        }
        return history;
    }

    void sendPacket(DatagramPacket packet, int packetReqId) {
        packet.setAddress(group);
        packet.setPort(PORT);
        int tries = 0;
        this.receivedData = null;
        try {
            do {
                System.out.println("Sending packet " + packetReqId + " to MS");
                socket.send(packet);
                tries++;
                // TODO buscar na waitlist
                synchronized (this) {
                    wait(TIMEOUT_TIME);
                    synchronized (RMIServer.waitList) {
                        this.receivedData = RMIServer.waitList.get(packetReqId);
                    }
                    if (this.receivedData != null) {
                        System.out.println("Nice, packet received " + this.receivedData.get("REQ_ID"));
                    } else {
                        System.out.println("Timed out, re-sending (" + tries + ")");
                    }
                }
            } while (this.receivedData == null);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Receiver extends Thread {
    MulticastSocket socket;
    byte[] buff = new byte[PacketBuilder.BUFF_SIZE];
    DatagramPacket packet = new DatagramPacket(buff, buff.length);

    public Receiver(MulticastSocket socket) {
        this.socket = socket;
        while (true) {
            System.out.println("Reciever ready to receive");
            try {
                socket.receive(packet);
                System.out.println("Reciever receieved new packet : " + new String(packet.getData()));
                HashMap<String, String> parsedData = PacketBuilder.parsePacketData(new String(packet.getData()));
                if (parsedData.get("TYPE").equals("REQUEST")) {
                    // TODO send notifications to users
                }
                int reqId = Integer.parseInt(parsedData.get("REQ_ID"));
                synchronized (RMIServer.waitList) {
                    RMIServer.waitList.put(reqId, parsedData);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}