package rmiserver;

import java.io.Serializable;
import java.util.Date;

class Search implements Serializable {
    Date date;
    String query;

    public Search(String query) {
        this.date = new Date(System.currentTimeMillis());
        this.query = query;
    }

    public Search(Date date, String query) {
        this.date = date;
        this.query = query;
    }
}
