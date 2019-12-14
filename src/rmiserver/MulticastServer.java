package rmiserver;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Main thread used to boostrap the rmiserver.MulticastServer.
 * Receives multicast packets and treats them accordingly.
 * Creates all the other necessary threads rmiserver.WebCrawler, rmiserver.Screamer and rmiserver.AdminNotificator.
 */
public class MulticastServer extends Thread {
    String MULTICAST_ADDRESS = "224.3.2.0";
    int id;
    int PORT = 4312;
    int BUFF_SIZE = 1024;
    HashMap<String, User> userList = new HashMap<>();
    HashMap<Integer, ServerInfo> servers = new HashMap<>();
    HashMap<String, Integer> searchCount = new HashMap<>();
    ArrayList<User> currentAdmins = new ArrayList<>();
    HashMap<String, String[]> currentAdminAddress = new HashMap<>();
    AdminData adminData = new AdminData();
    MulticastSocket socket;
    static WebCrawler crawler;
    Screamer screamer;
    AdminNotificator notificator;
    InetAddress group;

    File usersFile;
    File indexFile;
    File indexedPagesFile;
    File linksFile;
    File urlListFile;
    File searchCountFile;
    File adminDataFile;

    /**
     * Used to set all the main file descriptors and creates the rmiserver.MulticastServer thread
     *
     * @param args
     */
    public static void main(String[] args) {
        MulticastServer multicastServer = new MulticastServer();
        multicastServer.id = (args.length > 0) ? Integer.parseInt(args[0]) : 0;
        multicastServer.usersFile = new File("users" + multicastServer.id + ".bin");
        multicastServer.indexFile = new File("index" + multicastServer.id + ".bin");
        multicastServer.indexedPagesFile = new File("indexedPages" + multicastServer.id + ".bin");
        multicastServer.linksFile = new File("links" + multicastServer.id + ".bin");
        multicastServer.urlListFile = new File("urlList" + multicastServer.id + ".bin");
        multicastServer.searchCountFile = new File("searchCount" + multicastServer.id + ".bin");
        multicastServer.adminDataFile = new File("adminData" + multicastServer.id + ".bin");
        multicastServer.start();
        WebCrawler webCrawler = new WebCrawler(multicastServer);
        MulticastServer.crawler = webCrawler;
    }

    public void run() {
        socket = null;
        try {
            socket = new MulticastSocket(PORT);  // create socket and bind it
            group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            System.out.println("[Main] Begin loading data");
            try {
                loadData();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("[Main] dada loaded.");
            System.out.println("Users: " + userList.toString());
            System.out.println("Index : " + crawler.index.size());
            System.out.println("Linked pages: " + crawler.linkedPages.size());
            System.out.println("Indexed Pages: " + crawler.indexedPages.size());
            try {
                System.out.println("URL list: " + crawler.urlList.subList(0, 10).toString());
            } catch (Exception e) {

            }
            System.out.printf("Search count: " + searchCount.toString());
            System.out.println("[WebServer] Spawining child threads");
            // Starting webcrawler
            crawler.start();
            screamer = new Screamer(this);
            screamer.start();
            notificator = new AdminNotificator(this);
            notificator.start();

            while (true) {
                HashMap<String, String> parsedData = null;
                byte[] buffer = new byte[BUFF_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                System.out.println("[Main] Waiting for packets");
                socket.receive(packet);
                System.out.println("[Main] Received packet with message: " + new String(packet.getData()));
                InetAddress rmiServerAddress = packet.getAddress();
                //System.out.println("[Main] Adding new RMI address+port");
                synchronized (currentAdmins) {
                    notificator.RMIConnections.put(rmiServerAddress, packet.getPort());
                }
                String dataStr = new String(packet.getData());
                parsedData = PacketBuilder.parsePacketData(dataStr);
                int reqId = Integer.parseInt(parsedData.get("REQ_ID"));

                DatagramPacket response = null;
                ArrayList<DatagramPacket> extraResponses = new ArrayList<>();
                User user;

                // Special type of packet directed at the multicast server
                // Usually the multicast server receives REQUESTS
                // These packets are, as the name suggests, a response to the multicast server, namely to the
                // AdminNotification packet, that sometimes needs to be stored and forwarded when the user logs in
                if (parsedData.get("TYPE").equals("REPLY")) {
                    System.out.println("[Main] Received grant admin successfully delivered, removing from notification list");
                    if (parsedData != null && parsedData.containsKey("OPERATION")) {
                        switch (parsedData.get("OPERATION")) {
                            case "NOTIFICATION_DELIVERED":
                                user = userList.get(parsedData.get("USERNAME"));
                                user.pendingData.clear(); // TODO só ha uma notification, o user grand admin rights, adicionar mais
                                saveUsers();
                                break;
                        }
                    }
                    continue;
                }
                // Core of the Multicast Server. After receiving the packets redirects or treats it accordingly
                switch (parsedData.get("OPERATION")) {
                    case "REGISTER":
                        // Check if user exists
                        if (userList.containsKey(parsedData.get("USERNAME"))) {
                            response = PacketBuilder.ErrorPacket(reqId, PacketBuilder.RESULT.ER_USER_EXISTS);
                        } else {
                            // Create new user
                            boolean isAdmin = userList.isEmpty();
                            user = new User(parsedData.get("USERNAME"), parsedData.get("PASSWORD"), isAdmin);
                            userList.put(user.username, user);
                            saveUsers();
                            response = PacketBuilder.SuccessPacket(reqId);
                            //System.out.println("Registered users: " + userList.toString());
                        }
                        break;
                    case "LOGIN":
                        if (userList.containsKey(parsedData.get("USERNAME"))) {
                            user = userList.get(parsedData.get("USERNAME"));
                            // Check if correct password
                            if (user.password.equals(parsedData.get("PASSWORD"))) {
                                response = PacketBuilder.LoginSuccessPacket(reqId, user);
                                if (!user.pendingData.isEmpty()) {
                                    for (DatagramPacket notification : user.pendingData) {
                                        extraResponses.add(notification);
                                    }
                                }
                            } else {
                                response = PacketBuilder.ErrorPacket(reqId, PacketBuilder.RESULT.ER_WRONG_PASS);
                            }
                        } else {
                            response = PacketBuilder.ErrorPacket(reqId, PacketBuilder.RESULT.ER_NO_USER);
                        }
                        break;
                    case "SEARCH":
                        ArrayList<String> searchWords = PacketBuilder.getSearchWords(parsedData);
                        System.out.println("[Main] User wants to search reverse index for these words:");
                        System.out.println(searchWords.toString());
                        String username = parsedData.get("USERNAME");
                        // If logged user then save to hist search history

                        String search = String.join(" ", searchWords);
                        if (!username.equals("null")) { // TODO dont like this :[
                            user = userList.get(username); //TODO check USER ANOAN FICK
                            user.search_history.add(new Search(search));
                            saveUsers();
                        }

                        int currentCount = searchCount.getOrDefault(search, 0);
                        searchCount.put(search, ++currentCount);

                        //Update admin search
                        ArrayList<Map.Entry<String, Integer>> sortedTopSearches = new ArrayList<>(searchCount.entrySet());
                        Collections.sort(sortedTopSearches, new Comparator<Map.Entry<String, Integer>>() {
                            @Override
                            public int compare(Map.Entry<String, Integer> first, Map.Entry<String, Integer> second) {
                                return second.getValue() - first.getValue();
                            }
                        });
                        ArrayList<TopSearch> topSearches = new ArrayList<>();
                        for (Map.Entry<String, Integer> entry : sortedTopSearches) {
                            topSearches.add(new TopSearch(entry.getKey(), entry.getValue()));
                        }
                        int max = (topSearches.size() >= 10) ? 10 : topSearches.size();
                        topSearches.subList(0, max);
                        System.out.println("[Main] Current top searches.");
                        System.out.println(topSearches);
                        synchronized (this.adminData) {
                            this.adminData.topSearches = topSearches;
                            this.adminData.notify();
                            saveAdminData();
                        }
                        saveSearchCount();

                        ArrayList<Page> urls = findPagesWithWords(searchWords);
                        //
                        int page = Integer.parseInt(parsedData.get("PAGE"));
                        int lastIndex = 10 * (page + 1);
                        int maxIndex = (lastIndex > urls.size()) ? urls.size() : lastIndex;
                        urls = new ArrayList<>(urls.subList(10 * page, maxIndex));
                        response = PacketBuilder.SearchResults(reqId, urls);
                        break;
                    case "INDEX":
                        int currentLoad = 0;
                        synchronized (crawler.urlList) {
                            currentLoad = crawler.urlList.size();
                        }
                        // Check if it has the lowest load of all the Multicast Servers it knows
                        int minLoad = currentLoad;
                        ServerInfo minServerInfo = servers.get(this.id);
                        for (ServerInfo serverInfo : servers.values()) {
                            if (serverInfo.load < minLoad) {
                                minLoad = serverInfo.load;
                                minServerInfo = serverInfo;
                            }
                        }

                        if (minServerInfo != null && minServerInfo.id != this.id) {
                            // TODO send to other server
                        } else {
                            synchronized (crawler.urlList) {
                                crawler.urlList.add(0, parsedData.get("URL"));
                                crawler.urlList.notify();
                            }
                        }
                        response = PacketBuilder.SuccessPacket(reqId);

                        break;
                    case "HISTORY": // send user history
                        user = userList.get(parsedData.get("USERNAME"));
                        response = PacketBuilder.UserHistoryPacket(reqId, user.search_history);
                        break;
                    case "GRANT":
                        user = userList.get(parsedData.get("USERNAME"));
                        if (user == null) {
                            response = PacketBuilder.ErrorPacket(reqId, PacketBuilder.RESULT.ER_NO_USER);
                        } else {
                            user.admin = true;
                            userList.put(user.username, user);
                            response = PacketBuilder.SuccessPacket(reqId);
                            // TODO notify user
                            DatagramPacket notification = PacketBuilder.AdminNotificationPacket(reqId, user.username);
                            user.pendingData.add(notification);
                            extraResponses.add(notification);
                        }
                        break;
                    case "LINKED":
                        ArrayList<String> links = new ArrayList<>();
                        synchronized (crawler.linkedPages) {
                            String url = parsedData.get("URL");
                            if (!url.startsWith("http://") && !url.startsWith("https://"))
                                url = "https://".concat(url);
                            if (!url.endsWith("/"))
                                url = url.concat("/");
                            HashSet<String> backPages = crawler.linkedPages.get(url);
                            if (backPages != null)
                                links = new ArrayList<>(backPages);
                        }
                        response = PacketBuilder.LinksToPagePacket(reqId, links);
                        break;
                    case "DISCOVERY":
                        int id = Integer.parseInt(parsedData.get("REQ_ID"));
                        if (!this.servers.containsKey(id)) {
                            InetAddress address = InetAddress.getByName(parsedData.get("ADDRESS"));
                            int port = Integer.parseInt(parsedData.get("PORT"));
                            int load = Integer.parseInt(parsedData.get("LOAD"));
                            this.servers.put(id, new ServerInfo(id, address, port, load));

                            ArrayList<String> connecterServers = new ArrayList<>();
                            for (Map.Entry<Integer, ServerInfo> entry : servers.entrySet()) {
                                int serverId = entry.getValue().id;
                                int serverPORT = entry.getValue().port;
                                String addr = entry.getValue().address.toString();
                                connecterServers.add("ID: " + serverId + " " + addr + ":" + serverPORT);
                            }
                            synchronized (adminData) {
                                adminData.servers = connecterServers;
                                adminData.notify();
                                saveAdminData();
                            }
                        }
                        continue;
                    case "ADMIN_IN":
                        user = userList.get(parsedData.get("USERNAME"));
                        if (!currentAdmins.contains(user)) {
                            currentAdmins.add(user);
                            currentAdminAddress.put(user.username, new String[]{String.valueOf(packet.getAddress()), String.valueOf(packet.getPort())});
                        }
                        //System.out.println("ADMIN IN");
                        response = PacketBuilder.SuccessPacket(reqId);
                        synchronized (adminData) {
                            adminData.notify();
                        }
                        // TEST
                        break;
                    case "ADMIN_OUT":
                        user = userList.get(parsedData.get("USERNAME"));
                        currentAdmins.remove(user);
                        currentAdminAddress.remove(user.username);
                        //System.out.println("ADMIN OUT");
                        response = PacketBuilder.SuccessPacket(reqId);
                        break;
                    case "REQUEST_USER_LIST":
                        ArrayList<User> users = new ArrayList<User>(userList.values());
                        response = PacketBuilder.UserListPacket(reqId, users);
                        break;
                    default:
                        continue;
                }
                response.setPort(packet.getPort());
                response.setAddress(packet.getAddress());

                System.out.println("[MAIN] Sending answer to " + packet.getAddress() + " " + packet.getPort());

                socket.send(response);

                for (DatagramPacket extraPacket : extraResponses) {
                    extraPacket.setPort(packet.getPort());
                    extraPacket.setAddress(packet.getAddress());
                    socket.send(extraPacket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

    private void loadData() throws IOException, ClassNotFoundException {
        //System.out.println("Loading user list");
        userList = loadUsers();
        searchCount = loadSearchCount();
        loadAdminData();
        crawler.index = loadIndex();
        crawler.indexedPages = loadIndexedPages();
        crawler.urlList = loadUrlList();
        crawler.linkedPages = loadLinkedPagesList();
    }


    private ArrayList<String> loadUrlList() throws IOException, ClassNotFoundException {
        ArrayList<String> urlList = new ArrayList<>();
        if (urlListFile.exists() && urlListFile.length() > 0) {
            FileInputStream fs = new FileInputStream(urlListFile);
            ObjectInputStream os = new ObjectInputStream(fs);
            urlList = (ArrayList<String>) os.readObject();
            os.close();
            fs.close();
        } else {
            urlListFile.createNewFile();
        }
        return urlList;
    }

    void loadAdminData() {
        try {
            if (adminDataFile.exists() && adminDataFile.length() > 0) {
                FileInputStream fs = new FileInputStream(adminDataFile);
                ObjectInputStream os = new ObjectInputStream(fs);
                this.adminData = (AdminData) os.readObject();
                os.close();
                fs.close();
            } else {
                adminDataFile.createNewFile();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    void saveAdminData() {
        synchronized (this.adminData) {
            try {
                FileOutputStream fs = new FileOutputStream(adminDataFile);
                ObjectOutputStream os = new ObjectOutputStream(fs);
                os.writeObject(this.adminData);
                os.close();
                fs.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void saveUrlList() {
        try {
            FileOutputStream fs = new FileOutputStream(urlListFile);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(crawler.urlList);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, Integer> loadSearchCount() throws IOException, ClassNotFoundException {
        HashMap<String, Integer> urlList = new HashMap<>();
        if (searchCountFile.exists() && searchCountFile.length() > 0) {
            FileInputStream fs = new FileInputStream(searchCountFile);
            ObjectInputStream os = new ObjectInputStream(fs);
            urlList = (HashMap<String, Integer>) os.readObject();
            os.close();
            fs.close();
        } else {
            searchCountFile.createNewFile();
        }
        return urlList;
    }

    private void saveSearchCount() {
        try {
            FileOutputStream fs = new FileOutputStream(searchCountFile);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(searchCount);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, HashSet<String>> loadLinkedPagesList() throws IOException, ClassNotFoundException {
        HashMap<String, HashSet<String>> linkedPages = new HashMap<>();
        if (linksFile.exists() && linksFile.length() > 0) {
            FileInputStream fs = new FileInputStream(linksFile);
            ObjectInputStream os = new ObjectInputStream(fs);
            linkedPages = (HashMap<String, HashSet<String>>) os.readObject();
            os.close();
            fs.close();
        } else {
            linksFile.createNewFile();
        }
        return linkedPages;
    }

    void saveLinkedPages() {
        try {
            FileOutputStream fs = new FileOutputStream(linksFile);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(crawler.linkedPages);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HashSet<String> loadIndexedPages() throws IOException, ClassNotFoundException {
        HashSet<String> indexedPages = new HashSet<>();
        if (indexedPagesFile.exists() && indexedPagesFile.length() > 0) {
            FileInputStream fs = new FileInputStream(indexedPagesFile);
            ObjectInputStream os = new ObjectInputStream(fs);
            indexedPages = (HashSet<String>) os.readObject();
            os.close();
            fs.close();
        } else {
            indexedPagesFile.createNewFile();
        }
        return indexedPages;
    }

    void saveIndexedPages() {
        try {
            FileOutputStream fs = new FileOutputStream(indexedPagesFile);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(crawler.indexedPages);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, HashSet<Page>> loadIndex() throws IOException, ClassNotFoundException {
        HashMap<String, HashSet<Page>> index = new HashMap<>();
        if (indexFile.exists() && indexFile.length() > 0) {
            FileInputStream fs = new FileInputStream(indexFile);
            ObjectInputStream os = new ObjectInputStream(fs);
            index = (HashMap<String, HashSet<Page>>) os.readObject();
            os.close();
            fs.close();
        } else {
            indexFile.createNewFile();
        }
        return index;
    }

    void saveIndex() {
        try {
            FileOutputStream fs = new FileOutputStream(indexFile);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(crawler.index);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, User> loadUsers() throws IOException, ClassNotFoundException {
        HashMap users = new HashMap<>();
        if (usersFile.exists() && usersFile.length() > 0) {
            FileInputStream fs = new FileInputStream(usersFile);
            ObjectInputStream os = new ObjectInputStream(fs);
            users = (HashMap) os.readObject();
            os.close();
            fs.close();
        } else {
            usersFile.createNewFile();
        }
        return users;
    }

    private void saveUsers() {
        try {
            FileOutputStream fs = new FileOutputStream(usersFile);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(userList);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    ArrayList<Page> findPagesWithWords(ArrayList<String> words) {
        HashSet<Page> pages = new HashSet<>();
        ArrayList<Page> pageList;
        synchronized (crawler.index) {
            pages = crawler.index.get(words.get(0));
            if (pages == null) return new ArrayList<>();
            for (int i = 1; i < words.size(); i++) {
                HashSet<Page> second = crawler.index.get(words.get(i));
                if (second == null) return new ArrayList<>();
                pages.retainAll(second);
            }
            if (pages == null) {
                return new ArrayList<>();
            }
            pageList = new ArrayList<>(pages);

        }
        // TODO este sort esrtá errado, tem que ser pelo numero de links para a página e não
        // pelo numero de links que a pagina tem
        synchronized (crawler.linkedPages) {
            // crawler.linked.get(page.url).size()
            Collections.sort(pageList, new PageComparator());
            System.out.println("[Main] Search result sorted by number of links:");
            try {
                pageList.forEach(page -> System.out.println(page.url + " " + crawler.linkedPages.get(page.url).size()));
            } catch (NullPointerException e) {

            }
        }
        //System.out.println(pages.toString());
        return pageList;
    }

    class PageComparator implements Comparator<Page> {
        @Override
        public int compare(Page page, Page page2) {
            HashSet<String> linksToPage1 = crawler.linkedPages.get(page.url);
            HashSet<String> linksToPage2 = crawler.linkedPages.get(page2.url);

            if (linksToPage1 != null && linksToPage2 != null)
                return linksToPage2.size() - linksToPage1.size();
            else if (linksToPage1 != null)
                return 1;
            else if (linksToPage2 != null)
                return -1;
            return 0;
        }
    }
}

class WebCrawler extends Thread {

    MulticastServer ms;
    HashMap<String, HashSet<Page>> index = new HashMap<>();
    // url -> list of urls that link to this page
    HashMap<String, HashSet<String>> linkedPages = new HashMap<>();
    HashSet<String> indexedPages = new HashSet<>();
    ArrayList<String> urlList = new ArrayList<>();

    public WebCrawler(MulticastServer ms) {
        this.ms = ms;
    }

    @Override
    public void run() {
        (new Thread(new Saver())).start();
        System.out.println("[Crawler] Created");
        while (true) {
            String url;
            System.out.println("[Crawler] Waiting for links");
            synchronized (urlList) {
                while (urlList.isEmpty()) {
                    try {
                        urlList.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                url = urlList.remove(0);
            }
            Document doc = null;
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("localhost"))
                    url = "https://".concat(url);
                System.out.println("[Crawler] Got link: " + url);
                doc = Jsoup.connect(url).get();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
                continue; // As vezes acontece que o site nao dá
            }
            if (doc == null) {
                System.out.println("[Crawler] Ignored link: " + url + "\n HTML document was null");
                continue;
            }
            synchronized (indexedPages) {
                if (indexedPages.contains(url)) {
                    System.out.println("[Crawler] link is already indexed...");
                    continue; // TODO why is this necessary
                }
            }
            String title = doc.title().replaceAll("\\|", "").replaceAll(";", "");
            Elements metaDescription = doc.select("meta[name=description]");
            String description = " ";
            if (metaDescription.size() > 0) {
                description = metaDescription
                        .get(0)
                        .attr("content")
                        .replaceAll("\\|", "")
                        .replaceAll(";", "");
            }

            Page currentPage = new Page(url, title, description);

            // Get all page links
            Elements links = doc.select("a[href]");
            synchronized (urlList) { // TODO distribuir a carga com outros servers
                HashSet<String> linksInThisPage = new HashSet<>();
                for (Element link : links) {
                    //System.out.println(link.text() + "\n" + link.attr("abs:href") + "\n");
                    String linkSt = link.attr("abs:href");
                    if (linkSt.contains("@") || linkSt.isEmpty())
                        continue;
                    HashSet<String> linkedFrom = linkedPages.getOrDefault(linkSt, new HashSet<>());
                    linkedFrom.add(url);
                    synchronized (linkedPages) {
                        linkedPages.put(linkSt, linkedFrom);
                        int numLinksToThisLink = linkedFrom.size();
                        if (numLinksToThisLink > ms.adminData.minPagesLinks) {
                            synchronized (ms.adminData) {
                                System.out.println("[Crawler] new top site : " + linkSt + " new min: " + numLinksToThisLink);
                                ms.adminData.insertNewTopPage(linkSt, numLinksToThisLink);
                                ms.adminData.notify();
                            }
                        }
                    }
                    // Temos que verificar se a propria pagina n tem varios links para a mesma cena
                    if (!indexedPages.contains(linkSt) && !urlList.contains(linkSt) && !linkSt.equals(url)) {
                        urlList.add(linkSt);
                        //System.out.println("Added link to urllist: " + linkSt);
                    }
                }
            }

            // Get and index words
            StringTokenizer tokens = new StringTokenizer(doc.text());
            synchronized (index) {
                while (tokens.hasMoreElements()) {
                    String word = tokens.nextToken().toLowerCase();
                    HashSet<Page> updatedHaset = index.getOrDefault(word, new HashSet<>());
                    updatedHaset.add(currentPage);
                    index.put(word, updatedHaset);
                }
            }

            synchronized (indexedPages) {
                indexedPages.add(url);
            }

            System.out.println("[Crawler] Number of indexed words:" + index.keySet().size());
            System.out.println("[Crawler] Sleeping for 1sec");
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class Saver implements Runnable {
        int saveFrequencySeconds = 60;

        @Override
        public void run() {
            while (true) {
                synchronized (index) {
                    ms.saveIndex(); // save to file
                }
                synchronized (indexedPages) {
                    ms.saveIndexedPages();
                }
                synchronized (linkedPages) {
                    ms.saveLinkedPages();
                }
                synchronized (urlList) {
                    ms.saveUrlList();
                }
                try {
                    System.out.println("[Crawler] Saving to files. Current save freq: " + saveFrequencySeconds + " seconds");
                    sleep(saveFrequencySeconds * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

class Screamer extends Thread {

    MulticastServer server;

    public Screamer(MulticastServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        while (true) {
            System.out.println("[Screamer] started");
            int load;
            synchronized (server.crawler.urlList) {
                load = server.crawler.urlList.size();
            }
            // TODO reqId

            try {
                InetAddress group = InetAddress.getByName(server.MULTICAST_ADDRESS);
                DatagramPacket myStatus = PacketBuilder.DiscoveryPacket(server.id, InetAddress.getLocalHost().getHostAddress(), server.PORT, load);
                myStatus.setAddress(group);
                myStatus.setPort(server.PORT);
                server.socket.send(myStatus);
                System.out.println("[Screamer] Broadcast packet sent, sleeping 10sec");
                sleep(10000);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class AdminNotificator extends Thread {
    MulticastServer ms;
    HashMap<InetAddress, Integer> RMIConnections = new HashMap<>();

    public AdminNotificator(MulticastServer ms) {
        this.ms = ms;
    }

    @Override
    public void run() {
        while (true) {
            synchronized (ms.adminData) {
                try {
                    System.out.println("[Notificator] waiting for changes");
                    ms.adminData.wait();
                    System.out.println("[Notificator] Change received, sending packet to all admins");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (ms.currentAdmins) {
                    // Try send trough multicast
                    DatagramPacket packet = PacketBuilder.AdminPagePacket(0, ms.adminData, "admin");

                    for (Map.Entry<InetAddress, Integer> connection : RMIConnections.entrySet()) {
                        packet.setAddress(connection.getKey());
                        packet.setPort(connection.getValue());
                        try {
                            System.out.println("[NOTIFICATOR] Sending packet to " + connection.getKey() + " " + connection.getValue());
                            ms.socket.send(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    /*
                    for (User user : ms.currentAdmins) {
                        InetAddress addr = null;
                        int port = 0;
                        try {
                            // TODO porque isto tudo, podiamos ter mandado por multicast...
                            addr = InetAddress.getByName(ms.currentAdminAddress.get(user.username)[0].replaceAll("/", ""));
                            port = Integer.parseInt(ms.currentAdminAddress.get(user.username)[1]);
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }

                        DatagramPacket packet = PacketBuilder.AdminPagePacket(0, ms.adminData, user.username);
                        packet.setAddress(addr);
                        packet.setPort(port);

                        try {
                            ms.socket.send(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }*/
                }

            }
        }
    }
}

class ServerInfo {
    InetAddress address;
    int port;
    int load;
    int id;

    public ServerInfo(int id, InetAddress address, int port, int load) {
        this.id = id;
        this.address = address;
        this.port = port;
        this.load = load;
    }

    @Override
    public String toString() {
        return id + " " + address + " " + port + " load: " + load;
    }
}

