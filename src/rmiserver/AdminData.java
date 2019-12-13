package rmiserver;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

public class AdminData implements Serializable {
    private static final int MAX_SLOTS = 10;
    int topSearchesSlots = 10;
    int minPagesLinks = 0;
    ArrayList<TopPage> topPages = new ArrayList<>();
    ArrayList<String> topSearches = new ArrayList<>();
    ArrayList<String> servers = new ArrayList<>();

    public void insertNewTopSearch(String search, int count) {

    }

    public void insertNewTopPage(String url, int count) {
        TopPage newPage = new TopPage(url, count);
        for (int i = 0; i <= topPages.size(); i--) {
            if (topPages.size() == 0) {
                topPages.add(newPage);
                return;
            }
            TopPage tempPage = topPages.get(i);
            if (newPage.count >= tempPage.count) {
                topPages.add(i, newPage);
                break;
            }
        }
        if (topPages.size() > MAX_SLOTS) {
            topPages.remove(MAX_SLOTS);
        }
        minPagesLinks = topPages.get(topPages.size() - 1).count;
    }

}

class TopPage implements Serializable {
    int count;
    String url;

    public TopPage(String url, int count) {
        this.url = url;
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public String getUrl() {
        return url;
    }
}