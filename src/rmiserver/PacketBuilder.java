package rmiserver;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public class PacketBuilder {
    static int BUFF_SIZE = 1024;
    static String REPLY_TYPE = "TYPE|REPLY;";
    static String REQUEST_TYPE = "TYPE|REQUEST;";
    StringBuilder sb = new StringBuilder();

    public static DatagramPacket AdminNotificationPacket(int reqId, String username) {
        StringBuilder sb = new StringBuilder();
        String dataString = sb
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";") // TODO ver qual o req_id necessário. será que é mesmo ?
                .append("OPERATION|GRANT_ADMIN_NOTIFICATION;")
                .append("USERNAME|" + username + "\n")
                .toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket NotificationDelivered(int reqId, String name) {
        String dataString = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|NOTIFICATION_DELIVERED;")
                .append("USERNAME|" + name + "\n")
                .toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket RegisterPacket(int reqId, String username, String password) {
        StringBuilder sb = new StringBuilder();
        String dataString = sb
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|REGISTER;")
                .append("USERNAME|" + username + ";")
                .append("PASSWORD|" + password + "\n")
                .toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket LoginPacket(int reqId, String username, String password) {
        StringBuilder sb = new StringBuilder();
        String dataString = sb
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|LOGIN;")
                .append("USERNAME|" + username + ";")
                .append("PASSWORD|" + password + "\n").toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket LoginSuccessPacket(int reqId, User user) {
        byte[] data = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("RESULT|" + RESULT.SUCCESS + ";")
                .append("ADMIN|" + user.admin + "\n")
                .toString()
                .getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket SuccessPacket(int reqId) {
        return PacketBuilder.ErrorPacket(reqId, RESULT.SUCCESS); // TODO isAdmin tag
    }

    static DatagramPacket ErrorPacket(int reqId, RESULT result) {
        byte[] data = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("RESULT|" + result + "\n")
                .toString()
                .getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket SearchPacket(int reqId, String[] wordList, String user, int page) {
        StringBuilder sb = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|SEARCH;")
                .append("USERNAME|" + user + ";")
                .append("PAGE|" + page + ";")
                .append("WORD_COUNT|" + wordList.length + ";");
        for (int i = 0; i < wordList.length - 1; i++) {
            String entry = "WORD_" + i + "|" + wordList[i] + ";";
            sb.append(entry);
        }
        sb.append("WORD_" + (wordList.length - 1) + "|" + wordList[wordList.length - 1] + "\n");
        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket IndexPacket(int reqId, String url) {
        String dataString = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|INDEX;")
                .append("URL|" + url + "\n").toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket GrantAdmin(int reqId, String username) {
        String dataString = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|GRANT;")
                .append("USERNAME|" + username + "\n").toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket RequestHistoryPacket(int reqId, String name) {
        String dataString = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|HISTORY;")
                .append("USERNAME|" + name + "\n")
                .toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket UserHistoryPacket(int reqId, ArrayList<Search> history) {
        StringBuilder sb = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";");

        if (history.size() == 0) {
            sb.append("HIST_COUNT|" + history.size() + "\n");
        } else {
            sb.append("HIST_COUNT|" + history.size() + ";");
            for (int i = 0; i < history.size() - 1; i++) {
                sb.append("DATE_" + i + "|" + history.get(i).date + ";");
                sb.append("QUERY_" + i + "|" + history.get(i).query + ";");
            }
            sb.append("DATE_" + (history.size() - 1) + "|" + history.get((history.size() - 1)).date + ";");
            sb.append("QUERY_" + (history.size() - 1) + "|" + history.get((history.size() - 1)).query + "\n");
        }

        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    static HashMap<String, String> parsePacketData(String message) { //make this a class ?
        HashMap<String, String> hashMap = new HashMap<>();
        message = new StringTokenizer(message, "\n").nextToken();
        StringTokenizer tk = new StringTokenizer(message, ";");
        while (tk.hasMoreTokens()) {
            String token = tk.nextToken();
            // TODO check if token is invalid
            StringTokenizer splitter = new StringTokenizer(token, "|");
            if (splitter.countTokens() >= 2) {
                String key = splitter.nextToken();
                String val = splitter.nextToken();
                hashMap.put(key, val);
            } else {
                return null;
            }
        }
        return hashMap;
    }

    static ArrayList<String> getSearchWords(HashMap<String, String> searchParsedData) {
        ArrayList<String> words = new ArrayList<>();
        int wordCount = Integer.parseInt(searchParsedData.get("WORD_COUNT"));
        for (int i = 0; i < wordCount; i++) {
            words.add(searchParsedData.get("WORD_" + i));
        }
        return words;
    }

    static DatagramPacket URLListPacket(int reqId, ArrayList<String> urls) {
        StringBuilder sb = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("URL_COUNT|" + urls.size() + ";");
        for (int i = 0; i < urls.size() - 1; i++)
            sb.append("URL_" + i + "|" + urls.get(i) + ";");
        sb.append(String.format("URL_%d|%s\n", urls.size(), urls));
        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    static DatagramPacket SearchResults(int reqId, ArrayList<Page> pages) {
        StringBuilder sb = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";");
        if (pages.size() == 0)
            sb.append("PAGE_COUNT|" + pages.size() + "\n");
        else {
            sb.append("PAGE_COUNT|" + pages.size() + ";");
            for (int i = 0; i < pages.size() - 1; i++) {
                sb.append("URL_" + i + "|" + pages.get(i).url + ";")
                        .append("NAME_" + i + "|" + pages.get(i).name + ";")
                        .append("DESC_" + i + "|" + pages.get(i).description + ";");
            }
            sb.append(String.format("URL_%d|%s;", pages.size() - 1, pages.get(pages.size() - 1)))
                    .append("NAME_" + (pages.size() - 1) + "|" + pages.get(pages.size() - 1).name + ";")
                    .append("DESC_" + (pages.size() - 1) + "|" + pages.get(pages.size() - 1).description + "\n");
        }
        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    public static DatagramPacket GetLinksToPagePacket(int reqId, String url) {
        String dataString = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|LINKED;")
                .append("URL|" + url + "\n")
                .toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }

    public static DatagramPacket DiscoveryPacket(int reqId, String address, int port, int load) {
        String dataString = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|DISCOVERY;")
                .append("ADDRESS|" + address.replaceAll("/", "") + ";")
                .append("PORT|" + port + ";")
                .append("LOAD|" + load + "\n")
                .toString();
        byte[] data = dataString.getBytes();
        return new DatagramPacket(data, data.length);
    }


    public static DatagramPacket LinksToPagePacket(int reqId, ArrayList<String> links) {
        StringBuilder sb = new StringBuilder()
                .append(REPLY_TYPE)
                .append("REQ_ID|" + reqId + ";");
        if (links.size() == 0) {
            sb.append("LINK_COUNT|" + links.size() + "\n");
        } else {
            sb.append("LINK_COUNT|" + links.size() + ";");
            int last = links.size() - 1;
            for (int i = 0; i < last; i++) {
                sb.append("LINK_" + i + "|" + links.get(i) + ";");
            }
            sb.append("LINK_" + last + "|" + links.get(last) + "\n");
        }

        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    public static DatagramPacket AdminInLivePagePacket(int reqId, String user) {
        StringBuilder sb = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|ADMIN_IN;")
                .append("USERNAME|" + user + "\n");
        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    public static DatagramPacket AdminOutLivePagePacket(int reqId, String user) {
        StringBuilder sb = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("OPERATION|ADMIN_OUT;")
                .append("USERNAME|" + user + "\n");
        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }

    public static DatagramPacket AdminPagePacket(int reqId, AdminData adminData, String username) {
        StringBuilder sb = new StringBuilder()
                .append(REQUEST_TYPE)
                .append("REQ_ID|" + reqId + ";")
                .append("USERNAME|" + username + ";")
                .append("OPERATION|ADMIN_UPDATE;");
        // top searches
        int s = adminData.topSearches.size();
        sb.append("TOP_SEARCH_COUNT|" + s + ";");
        if (s != 0) {
            sb.append("TOP_SEARCH_COUNT|" + s + ";");
            int last = s;
            for (int i = 0; i < last; i++)
                sb.append("SEARCH_" + i + "|" + adminData.topSearches.get(i) + ";");
        }
        // top searches
        s = adminData.topPages.size();
        sb.append("TOP_PAGE_COUNT|" + s + ";");
        if (s != 0) {
            sb.append("TOP_PAGE_COUNT|" + s + ";");
            int last = s;
            for (int i = 0; i < last; i++)
                sb.append("PAGE_" + i + "|" + adminData.topPages.get(i) + ";");
        }

        // top server
        s = adminData.servers.size();
        if (s == 0) {
            sb.append("SERVER_COUNT|" + s + "\n");
        } else {
            sb.append("SERVER_COUNT|" + s + ";");
            int last = s - 1;
            for (int i = 0; i < last; i++) {
                sb.append("SERVER_" + i + "|" + adminData.servers.get(i) + ";");
            }
            sb.append("SERVER_" + last + "|" + adminData.servers.get(last) + "\n");
        }


        byte[] data = sb.toString().getBytes();
        return new DatagramPacket(data, data.length);
    }


    enum TYPE {
        REQUEST, REPLY
    }

    public enum RESULT {
        SUCCESS, ER_NO_USER, ER_WRONG_PASS, ER_USER_EXISTS
    }

    public enum OPERATIONS {

    }
}
