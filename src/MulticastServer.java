import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.*;

public class MulticastServer extends Thread {
    private String MULTICAST_ADDRESS = "224.3.2.0";
    private int PORT = 4312;
    int BUFF_SIZE = 1024;
    HashMap<String, User> userList = new HashMap<>();
    static WebCrawler crawler;
    File usersFile = new File("users.bin");
    File indexFile = new File("index.bin");


    public static void main(String[] args) {
        MulticastServer multicastServer = new MulticastServer();
        multicastServer.start();
        WebCrawler webCrawler = new WebCrawler(multicastServer);
        webCrawler.start();
        MulticastServer.crawler = webCrawler;
    }

    public void run() {
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(PORT);  // create socket and bind it

            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            // Load user list
            try {
                System.out.println("Loading user list");
                if (usersFile.exists())
                    userList = (HashMap<String, User>) (new ObjectInputStream(new FileInputStream(usersFile))).readObject();
                else {
                    usersFile.createNewFile();
                }
                if (indexFile.exists()) {
                    try {
                        FileInputStream fs = new FileInputStream(indexFile);
                        ObjectInputStream os = new ObjectInputStream(fs);
                        synchronized (crawler.index) {
                            crawler.index = (HashMap<String, HashSet<Page>>) os.readObject();
                        }
                    } catch (EOFException e) {

                    }
                } else {
                    indexFile.createNewFile();
                }
                System.out.println("Users: " + userList.toString());
                System.out.println("Index : " + crawler.index.toString());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

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
                User user;
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
                            userList.put(parsedData.get("USERNAME"), user);
                            (new ObjectOutputStream(new FileOutputStream(usersFile))).writeObject(userList);
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

                        response = PacketBuilder.SearchResults(reqId, urls);
                        break;
                    case "INDEX":
                        synchronized (crawler.url_list) {
                            crawler.url_list.add(parsedData.get("URL"));
                            crawler.url_list.notify();
                        }
                        response = PacketBuilder.SuccessPacket(reqId);
                        break;
                    case "HISTORY":
                        user = userList.get(parsedData.get("USER"));
                        response = PacketBuilder.UserHistoryPacket(reqId, user.search_history);
                        break;
                    default:
                        break;
                }
                response.setPort(packet.getPort());
                response.setAddress(packet.getAddress());
                socket.send(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

    ArrayList<Page> findPagesWithWords(ArrayList<String> words) {
        HashSet<Page> pages = new HashSet<>();
        ArrayList<Page> pageList;
        synchronized (crawler.index) {
            pages = crawler.index.get(words.get(0));
            for (int i = 1; i < words.size(); i++) {
                HashSet<Page> second = crawler.index.get(words.get(i));
                pages.retainAll(second);
            }

            pageList = new ArrayList<>(pages);

            Collections.sort(pageList, new Comparator<Page>() {
                @Override
                public int compare(Page page, Page page2) {
                    return page.links.size() - page2.links.size();
                }
            });
            System.out.println(pages.toString());
        }
        return pageList;
    }

    void saveUsers() {
        try {
            (new ObjectOutputStream(new FileOutputStream(usersFile))).writeObject(userList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

class WebCrawler extends Thread {

    MulticastServer ms;
    HashMap<String, HashSet<Page>> index = new HashMap<>();
    ArrayList<String> url_list = new ArrayList<>();

    public WebCrawler(MulticastServer ms) {
        this.ms = ms;
    }

    @Override
    public void run() {
        while (true) {
            String url;
            System.out.println("Web crawler thread started. Waiting for links");
            synchronized (url_list) {
                while (url_list.isEmpty()) {
                    try {
                        url_list.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                url = url_list.remove(0);
                System.out.println("Web Crawler got link :" + url);
            }
            Document doc = null;
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://"))
                    url = "http://".concat(url);
                doc = Jsoup.connect(url).get();
            } catch (IOException e) {

            }

            String title = doc.title().replaceAll("|", "").replaceAll(";", "");
            String description = doc
                    .select("meta[name=description]")
                    .get(0)
                    .attr("content")
                    .replaceAll("|", "")
                    .replaceAll(";", "");
            Page currentPage = new Page(url, title, description);

            Elements links = doc.select("a[href]");
            synchronized (url_list) { // TODO distribuir a carga com outros servers
                // TODO mostrar o title + desc to site
                for (Element link : links) {
                    //System.out.println(link.text() + "\n" + link.attr("abs:href") + "\n");
                    String linkSt = link.attr("abs:href");
                    //url_list.add(linkSt);
                    currentPage.links.add(linkSt);
                }
            }

            StringTokenizer tokens = new StringTokenizer(doc.text());
            while (tokens.hasMoreElements()) {
                String word = tokens.nextToken().toLowerCase();
                HashSet<Page> updatedHaset = index.getOrDefault(word, new HashSet<Page>());
                updatedHaset.add(currentPage);
                //System.out.println(doc.title());
                //System.out.println(url);
                // System.out.println(doc.body());
                index.put(word, updatedHaset);
            }

            try {
                System.out.println("Writing to file");
                FileOutputStream fs = new FileOutputStream(ms.indexFile);
                ObjectOutputStream os = new ObjectOutputStream(fs);
                os.writeObject(index);
                fs.close();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Words so far link: " + index.keySet().toString());
            //System.out.println("Link connections: " + linkedFrom.keySet().toString());
        }
    }
}

class User implements Serializable {
    String username;
    String password;
    boolean admin;
    ArrayList<Search> search_history = new ArrayList<>();

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