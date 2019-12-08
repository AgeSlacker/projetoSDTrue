package rmiserver;

import java.io.Serializable;
import java.util.HashSet;

public class Page implements Serializable {
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

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

