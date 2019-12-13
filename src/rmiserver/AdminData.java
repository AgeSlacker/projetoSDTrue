package rmiserver;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;

public class AdminData implements Serializable {
    private static final int MAX_SLOTS = 10;
    int topSearchesSlots = 10;
    int minPagesLinks = 0;
    public ArrayList<TopPage> topPages = new ArrayList<>();
    public ArrayList<String> topSearches = new ArrayList<>();
    public ArrayList<String> servers = new ArrayList<>();

    public void insertNewTopSearch(String search, int count) {

    }

    public void insertNewTopPage(String url, int count) {
        boolean updated = false;
        TopPage newPage = new TopPage(url, count);

        for (TopPage p : topPages) {
            if (p.url.equals(newPage.url)) {
                p.count = newPage.count;
                updated = true;
                break;
            }
        }
        if (!updated) topPages.add(newPage);
        topPages.sort(new Comparator<TopPage>() {
            @Override
            public int compare(TopPage o1, TopPage o2) {
                return o2.count - o1.count;
            }
        });

        if (topPages.size() > MAX_SLOTS) {
            topPages.remove(MAX_SLOTS);
            minPagesLinks = topPages.get(topPages.size() - 1).count;
        }
    }

}

