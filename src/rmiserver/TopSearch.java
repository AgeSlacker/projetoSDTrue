package rmiserver;

import java.io.Serializable;

public class TopSearch implements Serializable {
    public int count;
    public String search;

    public TopSearch(String search, int count) {
        this.search = search;
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public String getSearch() {
        return search;
    }
}


