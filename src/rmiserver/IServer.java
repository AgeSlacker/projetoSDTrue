package rmiserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IServer extends Remote {
    String sayHello() throws RemoteException;

    void subscribe(String name, IClient client) throws RemoteException;

    PacketBuilder.RESULT register(IClient client, String name, String password) throws RemoteException;

    PacketBuilder.RESULT login(IClient client, String name, String password) throws RemoteException;

    PacketBuilder.RESULT indexRequest(String url) throws RemoteException;

    ArrayList<Page> search(IClient client, String[] words, String user, int page) throws RemoteException;

    ArrayList<Search> getUserHistory(IClient client, String username) throws RemoteException;

    PacketBuilder.RESULT grantAdmin(IClient client, String user) throws RemoteException;

    ArrayList<String> getHyperLinks(String url) throws RemoteException;

    void unregister(String username) throws RemoteException;

    void adminInPage(String username) throws RemoteException;

    void adminOutPage(String username) throws RemoteException;

    void ping() throws RemoteException;

    /**
     * @param client
     * @param username
     * @throws RemoteException
     */
    void setLogged(IClient client, String username) throws RemoteException;
}
