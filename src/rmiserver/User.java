package rmiserver;

import java.io.Serializable;
import java.net.DatagramPacket;
import java.util.ArrayList;

public class User implements Serializable {
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

    public String getUsername() {
        return username;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    @Override
    public String toString() {
        return " Admin: " + this.admin + " " + this.username + " " + this.password;
    }
}
