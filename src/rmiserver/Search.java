package rmiserver;

import java.io.Serializable;
import java.util.Date;

public class Search implements Serializable {
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

    public Date getDate() {
        return date;
    }

    public String getQuery() {
        return query;
    }
}
