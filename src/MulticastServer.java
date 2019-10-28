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

public class MulticastServer extends Thread {
    String MULTICAST_ADDRESS = "224.3.2.0";
    int id;
    int PORT = 4312;
    int BUFF_SIZE = 1024;
    HashMap<String, User> userList = new HashMap<>();
    static WebCrawler crawler;
    HashMap<Integer, ServerInfo> servers = new HashMap<>();
    MulticastSocket socket;
    Screamer screamer;


    File usersFile;
    File indexFile;
    File indexedPagesFile;
    File linksFile;
    File urlListFile;

    public static void main(String[] args) {
        MulticastServer multicastServer = new MulticastServer();
        multicastServer.id = (args.length > 0) ? Integer.parseInt(args[0]) : 0;
        multicastServer.usersFile = new File("users" + multicastServer.id + ".bin");
        multicastServer.indexFile = new File("index" + multicastServer.id + ".bin");
        multicastServer.indexedPagesFile = new File("indexedPages" + multicastServer.id + ".bin");
        multicastServer.linksFile = new File("links" + multicastServer.id + ".bin");
        multicastServer.urlListFile = new File("urlList" + multicastServer.id + ".bin");
        multicastServer.start();
        WebCrawler webCrawler = new WebCrawler(multicastServer);
        MulticastServer.crawler = webCrawler;
    }

    public void run() {
        socket = null;
        try {
            socket = new MulticastSocket(PORT);  // create socket and bind it
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            screamer = new Screamer(this);
            screamer.start();
            System.out.println("Begin loading data");
            try {
                loadData();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("Initialization ended.");
            System.out.println("Users: " + userList.toString());
            System.out.println("Index : " + crawler.index.toString());
            System.out.println("Linked pages: " + crawler.linkedPages.toString());
            System.out.println("Indexed Pages: " + crawler.indexedPages.toString());
            System.out.println("URL list: " + crawler.urlList.toString());

            // Starting webcrawler
            crawler.start();

            while (true) {
                HashMap<String, String> parsedData = null;
                byte[] buffer = new byte[BUFF_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                System.out.println("MS Receiver waiting for packets");
                socket.receive(packet);
                System.out.println("MS Received packet with message: " + new String(packet.getData()));
                String dataStr = new String(packet.getData());
                parsedData = PacketBuilder.parsePacketData(dataStr);
                int reqId = Integer.parseInt(parsedData.get("REQ_ID"));

                DatagramPacket response = null;
                ArrayList<DatagramPacket> extraResponses = new ArrayList<>();
                User user;
                if (parsedData.get("TYPE").equals(PacketBuilder.REPLY_TYPE)) {
                    System.out.println("Received grant admin successfully delivered, removing from notification list");
                    switch (parsedData.get("OPERATION")) {
                        case "NOTIFICATION_DELIVERED":
                            user = userList.get(parsedData.get("USERNAME"));
                            user.pendingData.clear(); // TODO só ha uma notification, o user grand admin rights, adicionar mais
                            saveUsers();
                            break;
                    }
                    continue;
                }

                switch (parsedData.get("OPERATION")) {
                    case "REGISTER":
                        // Check if user exists
                        if (userList.containsKey(parsedData.get("USERNAME"))) {
                            packet = PacketBuilder.ErrorPacket(reqId, PacketBuilder.RESULT.ER_USER_EXISTS);
                            System.out.println("TODO User already exists");
                        } else {
                            // Create new user
                            boolean isAdmin = userList.isEmpty();
                            user = new User(parsedData.get("USERNAME"), parsedData.get("PASSWORD"), isAdmin);
                            userList.put(user.username, user);
                            saveUsers();
                            response = PacketBuilder.SuccessPacket(reqId);
                            System.out.println("Registered users: " + userList.toString());
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
                        System.out.println("User wants to search reverse index for these words:");
                        System.out.println(searchWords.toString());
                        String username = parsedData.get("USER");
                        // If logged user then save to hist search history
                        if (!username.equals("null")) { // TODO dont like this :[
                            user = userList.get(username); //TODO check USER ANOAN FICK
                            user.search_history.add(new Search(String.join(" ", searchWords)));
                            saveUsers();
                        }

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

                        if (minServerInfo.id != this.id) {
                            // TODO send to other server
                        } else {
                            synchronized (crawler.urlList) {
                                crawler.urlList.add(parsedData.get("URL"));
                                crawler.urlList.notify();
                            }
                        }
                        response = PacketBuilder.SuccessPacket(reqId);

                        break;
                    case "HISTORY": // send user history
                        user = userList.get(parsedData.get("USER"));
                        response = PacketBuilder.UserHistoryPacket(reqId, user.search_history);
                        break;
                    case "GRANT":
                        user = userList.get(parsedData.get("USER"));
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
                            HashSet<String> backPages = crawler.linkedPages.get(url);
                            if (backPages != null)
                                links = new ArrayList<>(backPages);
                        }
                        response = PacketBuilder.LinksToPagePacket(reqId, links);
                        break;
                    case "DISCOVERY":
                        int id = Integer.parseInt(parsedData.get("REQ_ID"));
                        InetAddress address = InetAddress.getByName(parsedData.get("ADDRESS"));
                        int port = Integer.parseInt(parsedData.get("PORT"));
                        int load = Integer.parseInt(parsedData.get("LOAD"));
                        this.servers.put(id, new ServerInfo(this.id, address, port, load));
                        System.out.println(this.servers.toString());
                        continue;
                    default:
                        break;
                }
                response.setPort(packet.getPort());
                response.setAddress(packet.getAddress());
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
        System.out.println("Loading user list");
        userList = loadUsers();
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
            FileOutputStream fs = new FileOutputStream(indexFile, true);
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
            try {
                pageList.forEach(page -> System.out.println(page.url + " " + crawler.linkedPages.get(page.url).size()));
            } catch (NullPointerException e) {
                System.out.println("GOT YOU");
            }
        }
        System.out.println(pages.toString());
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
        System.out.println("Crawler created");
        while (true) {
            String url;
            System.out.println("Web crawler thread started. Waiting for links");
            synchronized (urlList) {
                while (urlList.isEmpty()) {
                    try {
                        urlList.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                url = urlList.remove(0);
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Document doc = null;
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://"))
                    url = "https://".concat(url);
                doc = Jsoup.connect(url).get();
            } catch (IOException e) {

            } catch (IllegalArgumentException e) {
                continue; // As vezes acontece que o site nao dá
            }
            if (doc == null) {
                System.out.println("Ignored link: " + url + "\n HTML document was null");
                continue;
            }
            System.out.println("Web Crawler got link :" + url);
            synchronized (indexedPages) {
                if (indexedPages.contains(url)) {
                    System.out.println("This link is already indexed...");
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
                for (Element link : links) {
                    //System.out.println(link.text() + "\n" + link.attr("abs:href") + "\n");
                    String linkSt = link.attr("abs:href");
                    if (linkSt.contains("@"))
                        continue;
                    HashSet<String> linkedFrom = linkedPages.getOrDefault(linkSt, new HashSet<>());
                    linkedFrom.add(url);
                    synchronized (linkedPages) {
                        linkedPages.put(linkSt, linkedFrom);
                    }
                    // Temos que verificar se a propria pagina n tem varios links para a mesma cena
                    if (!indexedPages.contains(linkSt) && !urlList.contains(linkSt) && !linkSt.equals(url)) {
                        urlList.add(linkSt);
                        System.out.println("Added link to urllist: " + linkSt);
                    }
                }
            }

            // Get and index words
            StringTokenizer tokens = new StringTokenizer(doc.text());
            while (tokens.hasMoreElements()) {
                String word = tokens.nextToken().toLowerCase();
                HashSet<Page> updatedHaset = index.getOrDefault(word, new HashSet<>());
                updatedHaset.add(currentPage);
                index.put(word, updatedHaset);
            }

            synchronized (index) {
                ms.saveIndex(); // save to file
            }
            synchronized (indexedPages) {
                indexedPages.add(url);
                ms.saveIndexedPages();
            }
            synchronized (links) {
                ms.saveLinkedPages();
            }
            synchronized (urlList) {
                ms.saveUrlList();
            }

            System.out.println("Words so far link: " + index.keySet().toString());
            //System.out.println("Link connections: " + linkedFrom.keySet().toString());
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
            System.out.println("Screamer started");
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
                System.out.println("Packet sent, sleeping");
                sleep(10000);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class User implements Serializable {
    String username;
    String password;
    boolean admin;
    ArrayList<Search> search_history = new ArrayList<>();
    ArrayList<DatagramPacket> pendingData = new ArrayList<>();

    public User(String username, String password, boolean isAdmin) {
        this.username = username;
        this.password = password;
        this.admin = isAdmin;
    }

    @Override
    public String toString() {
        return " Admin: " + this.admin + " " + this.username + " " + this.password;
    }
}

class Page implements Serializable {
    String url;
    String name;
    String description;
    HashSet<String> links;

    public Page(String url, String name, String description) {
        this.name = name;
        this.url = url;
        this.description = description;
        this.links = new HashSet<>();
    }

    @Override
    public boolean equals(Object obj) {

        return this.url.equals(((Page) obj).url);
    }

    @Override
    public String toString() {
        return url;
    }
}

class Search implements Serializable {
    Date time;
    String query;

    public Search(String query) {
        this.time = new Date(System.currentTimeMillis());
        this.query = query;
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