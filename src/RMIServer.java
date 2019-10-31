import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RMIServer extends UnicastRemoteObject implements IServer {

    static HashMap<Integer, HashMap<String, String>> waitList = new HashMap<>();
    private static String MULTICAST_ADDRESS = "224.3.2.0";
    static HashMap<String, IClient> loggedUsers = new HashMap<>(); // TODO change to syncronized hashmap ?
    static HashMap<String, ArrayList<String>> notifications = new HashMap<>();
    int TIMEOUT_TIME = 1000;
    MulticastSocket socket;
    InetAddress group;
    AtomicInteger reqId = new AtomicInteger(0);
    HashMap<String, String> receivedData = null;
    private int PORT = 4312;
    public boolean isBackupServer = false;
    public IServer mainServer;
    static String rmiAddress;
    static int rmiPort;
    static String rmiLocation;

    /**
     * @throws RemoteException
     */
    public RMIServer() throws RemoteException {
        super();
        System.getProperties().put("java.security.policy", "policy.all");
        try {
            socket = new MulticastSocket();
            group = InetAddress.getByName(MULTICAST_ADDRESS);
            LocateRegistry.getRegistry(rmiPort).bind("RMIserver", this);
        } catch (RemoteException e) {
            e.printStackTrace(); // TODO see if possible new backup server without backup status
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (AlreadyBoundException e) {
            try {
                System.out.println("Server RMIserver already exists, testing connection");
                IServer boundServer = (IServer) Naming.lookup(rmiLocation);
                boundServer.ping();
                System.out.println("Server responding, binding as backup server");
                // Ping sucessful, re-bind as Backup
                System.out.println("");
                //LocateRegistry.getRegistry(7000).rebind("RMIserverBACKUP", this);
                this.isBackupServer = true;
                this.mainServer = boundServer;
            } catch (NotBoundException ex) {
                ex.printStackTrace();
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            } catch (RemoteException ex) {
                try {
                    System.out.println("Server failed to respond, binding as main RMIserver");
                    LocateRegistry.getRegistry(rmiPort).rebind("RMIserver", this);
                } catch (RemoteException exc) {
                    exc.printStackTrace();
                }
            }
        }

        if (this.isBackupServer) {
            int failedPing = 0;
            while (failedPing < 5) {
                try {
                    mainServer.ping();
                    failedPing = 0;
                    Thread.sleep(1000);
                } catch (RemoteException e) {
                    System.out.println("Main server failed ping " + failedPing);
                    failedPing++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            System.out.println("Main server timed out, we have a new main server in town");
            LocateRegistry.getRegistry(rmiPort).rebind("RMIserver", this);
            try {
                socket = new MulticastSocket();
                group = InetAddress.getByName(MULTICAST_ADDRESS);
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.mainServer = null;
            this.isBackupServer = false;
            (new Receiver(socket)).start();
        } else {
            System.out.println("RMI server waiting to receive remote calls");
            (new Receiver(socket)).start();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Invalid number of arguments\nUsage: RMIServer rmiIP rmiPORT");
        }
        rmiAddress = args[0];
        rmiPort = Integer.parseInt(args[1]);
        rmiLocation = "//" + rmiAddress + ":" + rmiPort + "/RMIserver";
        try {
            new RMIServer();
        } catch (RemoteException e) {
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
            if (result == PacketBuilder.RESULT.SUCCESS) {
                loggedUsers.put(name, client);
            }
            System.out.println("Current logged users: " + loggedUsers.keySet().toString());
        }

        ArrayList<String> notifications = RMIServer.notifications.get(name);
        if (notifications != null && !notifications.isEmpty()) {
            client.printMessage(notifications.remove(0));
            DatagramPacket notificationDelivered = PacketBuilder.NotificationDelivered(packetReqId, name);
            notificationDelivered.setAddress(group);
            notificationDelivered.setPort(PORT);
            try {
                socket.send(notificationDelivered);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    @Override
    public void setLogged(IClient client, String username) throws RemoteException {
        RMIServer.loggedUsers.put(username, client);
    }

    @Override
    public PacketBuilder.RESULT indexRequest(String url) throws RemoteException {
        url.replaceAll("\n", "").replaceAll(" ", "");
        int packerReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.IndexPacket(packerReqId, url);
        sendPacket(packet, packerReqId);
        return PacketBuilder.RESULT.valueOf(receivedData.get("RESULT"));
    }

    @Override
    public String[] search(IClient client, String[] words, String user, int page) throws RemoteException {
        int packetReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.SearchPacket(packetReqId, words, user, page);
        sendPacket(packet, packetReqId);
        client.printMessage("Seach complete");
        String[] result = new String[10];
        for (int i = 0; i < Integer.parseInt(receivedData.get("PAGE_COUNT")); i++) {
            String url = receivedData.get("URL_" + i);
            String name = receivedData.get("NAME_" + i);
            String desc = receivedData.get("DESC_" + i);
            result[i] = name + "\n" + url + "\n Description:" + desc + "\n";
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

    @Override
    public PacketBuilder.RESULT grantAdmin(IClient client, String user) throws RemoteException {
        int packerReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.GrantAdmin(packerReqId, user);
        sendPacket(packet, packerReqId);
        return PacketBuilder.RESULT.valueOf(receivedData.get("RESULT"));
    }

    @Override
    public ArrayList<String> getHyperLinks(String url) throws RemoteException {
        int packerReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.GetLinksToPagePacket(packerReqId, url);
        sendPacket(packet, packerReqId);
        ArrayList<String> links = new ArrayList<>();
        int link_count = Integer.parseInt(receivedData.get("LINK_COUNT"));
        for (int i = 0; i < link_count; i++) {
            links.add(receivedData.get("LINK_" + i));
        }
        return links;
    }

    @Override
    public void unregister(String username) {
        RMIServer.loggedUsers.remove(username);
    }

    @Override
    public void adminInPage(String username) throws RemoteException {
        int packerReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.AdminInLivePagePacket(packerReqId, username);
        sendPacket(packet, packerReqId);
        return;
    }

    @Override
    public void adminOutPage(String username) throws RemoteException {
        int packerReqId = reqId.getAndIncrement();
        DatagramPacket packet = PacketBuilder.AdminOutLivePagePacket(packerReqId, username);
        sendPacket(packet, packerReqId);
        return;
    }

    @Override
    public void ping() throws RemoteException {
        return;
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
                synchronized (RMIServer.waitList) {
                    RMIServer.waitList.wait(TIMEOUT_TIME);
                    this.receivedData = RMIServer.waitList.get(packetReqId);
                }

                if (this.receivedData != null) {
                    System.out.println("Nice, packet received " + this.receivedData.get("REQ_ID"));
                } else {
                    System.out.println("Timed out, re-sending (" + tries + ")");
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
    byte[] buff = new byte[60000]; // TODO check this size baby
    DatagramPacket packet = new DatagramPacket(buff, buff.length);

    public Receiver(MulticastSocket socket) {
        this.socket = socket;
        while (true) {
            System.out.println("Reciever ready to receive");
            try {
                socket.receive(packet);
                System.out.println("Reciever receieved new packet : " + new String(packet.getData()));
                HashMap<String, String> parsedData = PacketBuilder.parsePacketData(new String(packet.getData()));
                int reqId = Integer.parseInt(parsedData.get("REQ_ID"));
                if (parsedData.get("TYPE").equals("REQUEST")) {
                    switch (parsedData.get("OPERATION")) {
                        case "GRANT_ADMIN_NOTIFICATION":
                            // TODO send notifications to users
                            // TODO send notifications to users
                            IClient client = RMIServer.loggedUsers.get(parsedData.get("USERNAME"));
                            if (client == null) {
                                System.out.println("User not logged in, should be delivered latter");
                                String username = parsedData.get("USERNAME");
                                ArrayList<String> notifications = RMIServer.notifications.getOrDefault(username, new ArrayList<>());
                                notifications.add("You've been granted admin rights!");
                                RMIServer.notifications.put(parsedData.get("USERNAME"), notifications);
                                continue;
                            }
                            client.setAdmin();
                            DatagramPacket removeNotification = PacketBuilder.NotificationDelivered(reqId, parsedData.get("USERNAME"));
                            removeNotification.setAddress(packet.getAddress());
                            removeNotification.setPort(packet.getPort());
                            socket.send(removeNotification);
                            client.printMessage("You're an admin now!! yay");
                            break;
                        case "ADMIN_UPDATE":
                            String user = parsedData.get("USERNAME");
                            String message = "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"; // :'(
                            int count = Integer.parseInt(parsedData.get("SERVER_COUNT"));
                            message = message + "Servers:\n";
                            for (int i = 0; i < count; i++) {
                                message = message + parsedData.get("SERVER_" + i) + "\n";
                            }
                            message = message + "Top searches: ";
                            count = Integer.parseInt(parsedData.get("TOP_SEARCH_COUNT"));
                            for (int i = 0; i < count; i++) {
                                message = message + parsedData.get("SEARCH_" + i) + "\n";
                            }

                            count = Integer.parseInt(parsedData.get("TOP_PAGE_COUNT"));
                            for (int i = 0; i < count; i++) {
                                message = message + parsedData.get("PAGE_" + i) + "\n";
                            }
                            client = RMIServer.loggedUsers.get(user);
                            client.printMessage(message + "\n\n Type ENTER to exit");
                            break;
                    }
                } else {
                    synchronized (RMIServer.waitList) {
                        RMIServer.waitList.put(reqId, parsedData);
                        RMIServer.waitList.notifyAll();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}