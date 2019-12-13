package rmiserver;

import java.io.Serializable;

public class TopPage implements Serializable {
    public int count;
    public String url;

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
