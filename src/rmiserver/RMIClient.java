package rmiserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.StringTokenizer;

public class RMIClient extends UnicastRemoteObject implements IClient {
    BufferedReader inputStream;
    IServer server;
    char[] buffer = new char[1024];
    // Login outra vez guardando a sessão

    protected RMIClient() throws RemoteException {
        super();
        try {
            server = (IServer) Naming.lookup("//localhost:7000/RMIserver");
            System.out.println("Executing remote call");
            server.subscribe("client", this);
            System.out.println(server.sayHello());
        } catch (ConnectException e) {
            try {
                server = (IServer) Naming.lookup("//localhost:7000/RMIserverBACKUP");
                System.out.println("Backup server connected");
            } catch (NotBoundException ex) {
                System.err.println("No associated binding - no main nor backup server running.");
                e.printStackTrace();
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            }
        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        this.inputStream = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st;
        System.out.println("Type H or Help for a list of commands");
        while (true) {
            System.out.print("> ");
            try {
                int n = inputStream.read(buffer);
                String s = new String(buffer, 0, n - 1);
                s.replaceAll("|", "").replaceAll(";", "");
                System.out.println("String lida: " + s);
                st = new StringTokenizer(s, " ");
                if (!st.hasMoreElements()) continue; // Ignora empty strings
                switch (st.nextToken()) {
                    case "Register":
                        String name = st.nextToken();
                        String password = st.nextToken();
                        System.out.println("A pedir registo com name: " + name + " password: " + password);
                        PacketBuilder.RESULT success = server.register(this, name, password);
                        switch (success) {
                            case ER_USER_EXISTS:
                                System.out.println("Já existe um user com esse nome. Escolha outro\n>");
                                break;
                            case SUCCESS:
                                System.out.printf("You're logged in!");
                                break;
                        }
                        break;
                    case "Login":
                        name = st.nextToken();
                        password = st.nextToken();
                        success = server.login(this, name, password);
                        switch (success) {
                            case SUCCESS:
                                System.out.println("Login sucess");
                                break;
                            case ER_NO_USER:
                                System.out.println("No user");
                                break;
                            case ER_WRONG_PASS:
                                System.out.println("Wrong pass");
                                break;
                            default:
                                break;
                        }
                        break;
                    case "index":
                        String url = st.nextToken();
                        server.indexRequest(url);
                    default:

                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        try {
            new RMIClient();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void printMessage(String message) throws RemoteException {
        System.out.println("Message from Server:" + message);
    }

    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }

    @Override
    public void setAdmin() throws RemoteException {
        return;
    }
}