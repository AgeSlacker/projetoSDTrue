import java.io.IOException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Scanner;

public class RMIClient2 extends UnicastRemoteObject implements IClient {
    IServer server;
    Scanner sc = new Scanner(System.in);
    char[] buffer = new char[1024];
    String username;
    String password;
    boolean isAdmin;

    public static void main(String[] args) {
        try {
            IServer server = (IServer) Naming.lookup("//localhost:7000/RMIserver");
            IClient client = new RMIClient2(server);
            System.out.println("Executing remote call");
            server.subscribe("client", client);
            System.out.println(server.sayHello());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    protected RMIClient2(IServer server) throws RemoteException {
        super();
        this.server = server;
        int choice1 = -1;
        while (choice1 != 0) {
            // User should not be logged in
            this.username = null;
            this.password = null;
            choice1 = mainMenu();
            switch (choice1) {
                case 1: //login
                    do {
                        System.out.println("Please insert your credentials!(0 to cancel)");
                        System.out.print("Login:");
                        String login = sc.nextLine();
                        if (login.equals("0"))
                            break;
                        System.out.print("Password: ");
                        String password = sc.nextLine();
                        System.out.println("Consulting server...");
                        PacketBuilder.RESULT answer = null;
                        try {
                            answer = server.login(this, login, password);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        switch (answer) {
                            case SUCCESS:
                                this.username = login;
                                this.password = password;
                                loggedUser();
                                choice1 = -1;
                                break;
                            case ER_NO_USER:
                                System.out.println("User not in database");
                                break;
                            case ER_WRONG_PASS:
                                System.out.println("Password does not match");
                                break;
                        }
                    } while (choice1 == 1);
                    break;
                case 2: //register
                    label:
                    do {
                        System.out.println("--------Register Page------------");
                        System.out.println("Please insert your new credentials!(0 to cancel)");
                        System.out.print("Login:");
                        String login = sc.nextLine();
                        if (login.equals("0"))
                            break;
                        System.out.print("Password: ");
                        String password = sc.nextLine();
                        System.out.println("Consulting server...");
                        PacketBuilder.RESULT success = null;
                        try {
                            success = server.register(this, login, password);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        switch (success) {
                            case SUCCESS:
                                break label;
                            case ER_USER_EXISTS:
                                System.out.println("User already exists in database");
                                break;
                        }
                    } while (true);
                    break;
                case 3: //Anon Search
                    System.out.print("Type in the terms you want to search:");
                    String termos = sc.nextLine();
                    String[] words = termos.split(" ");
                    String[] results = server.search(this, words, this.username);
                    System.out.println("Search results:");
                    for (String result : results)
                        if (result != null)
                            System.out.println(result);
                    System.out.println("Press enter to exit");
                    String s = sc.nextLine();
                    break;
                case 0: //exit
                    break;
                default:
                    break;
            }
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
        this.isAdmin = true;
    }

    public int mainMenu() {

        int choice = 0;
        do {
            System.out.println("----------------------------------");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Anonymous search");
            System.out.println("0. Exit");
            System.out.println("----------------------------------");
            System.out.print("Choose an option: ");
            choice = Integer.parseInt(sc.nextLine());
            if ((choice < 0 || choice > 3))
                System.out.println("Not valid choice: " + choice);
        } while (choice < 0 || choice > 3);
        return choice;
    }

    public int loggedUserMenu() {
        int choice = 0;
        do {
            System.out.println("----------------------------------");
            System.out.println("1. Search");
            System.out.println("2. History");
            System.out.println("3. Consult pages hyperlinks");
            if (this.isAdmin) {
                System.out.println("4. Give Permission");
                System.out.println("5. List Users");
                System.out.println("6. Index URL ");
                System.out.println("7. System info");
            }
            System.out.println("0. Exit");
            System.out.println("----------------------------------");
            System.out.print("Choose an option: ");
            choice = Integer.parseInt(sc.nextLine());
            if ((this.isAdmin && (choice >= 0 && choice <= 7)) || (choice >= 0 || choice <= 3))
                break;
            System.out.println("Not valid choice: " + choice);

        } while (true);
        return choice;
    }

    public void loggedUser() {
        int choice = 1;
        while (choice != 0) {
            choice = loggedUserMenu();
            switch (choice) {
                case 1: //search
                    System.out.print("Search words (space separated): ");
                    String[] words = sc.nextLine().split(" ");

                    try {
                        String[] results = server.search(this, words, this.username);
                        for (String result : results)
                            System.out.println(result);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                case 2: //history
                    printUserHistory();
                    break;
                case 3: //consult Hyperlink pages

                    break;
                case 4: //give permission

                    break;
                case 5: //list users

                    break;
                case 6: //index Url
                    // index URL rmi call
                    System.out.print("URL: ");
                    String url = sc.nextLine();
                    try {
                        PacketBuilder.RESULT result = server.indexRequest(url);
                        if (result.equals(PacketBuilder.RESULT.SUCCESS)) {
                            System.out.println("URL indexed");
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                case 7: //system info

                    break;
                case 0: //exit

                    break;
                default:
                    break;
            }
        }
    }

    void printUserHistory() {
        try {
            ArrayList<String> history = server.getUserHistory(this, username);
            System.out.println("Your history:");
            history.forEach(hist -> System.out.println(hist));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void loggedUserSearch() {
        System.out.print("Type in the terms you want to search:");
        String termos = sc.nextLine();
        /*
        Inserir envio dos termos e print do recebido
         */
    }

    public void loggedUserHyperlink() {
        System.out.print("Type in the number of hyperlinks you are looking for:");
        String termos = sc.nextLine();
        /*
        Inserir envio dos termos e print do recebido
         */
    }

    public void givePermissions() {
        System.out.println("Type a username of a user you want to turn into an admin:");
        String username = sc.nextLine();
        /*
        Inserir envio dos termos e print do recebido
         */
    }

    public void indexURL() {
        System.out.println("Type the URL you want to Index:");
        String url = sc.nextLine();
        /*
        Inserir envio dos termos e print do recebido
         */
    }

    public void systemInfo() {
        /*
        Inserir envio dos termos e print do recebido
         */
    }
}