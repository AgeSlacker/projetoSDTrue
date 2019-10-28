import java.io.IOException;
import java.net.MalformedURLException;
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
    int retries;

    public static void main(String[] args) {
        try {
            IServer server = (IServer) Naming.lookup("//localhost:7000/RMIserver");
            IClient client = new RMIClient2(server);
            System.out.println("Executing remote call");
            //server.subscribe("client", client);
            System.out.println(server.sayHello());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    void rebindServer() {
        while (retries < 60) {
            try {
                this.server = (IServer) Naming.lookup("//localhost:7000/RMIserver");
                break; // rebound successfully
            } catch (NotBoundException e) {

            } catch (MalformedURLException e) {
                //e.printStackTrace();
            } catch (RemoteException e) {
                //e.printStackTrace();
            }
            retries++;
            System.out.println("Rebind failed (" + retries + ")");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Rebind successful");
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
                        String login = untilNotEmpty("Login: ", "Login cannot be empty!");
                        if (login.equals("0"))
                            break;
                        String password = untilNotEmpty("Password: ", "Password cannot be empty!");
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
                    do {
                        System.out.println();
                        System.out.println("--------Register Page------------");
                        System.out.println("Please insert your new credentials!(0 to cancel)");
                        String login = untilNotEmpty("Login:", "Login cannot be empty!");
                        if (login.equals("0"))
                            break;
                        String password = untilNotEmpty("Password: ", "Password cannot be empty!");
                        System.out.println("Consulting server...");
                        PacketBuilder.RESULT success = null;
                        try {
                            success = server.register(this, login, password);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        switch (success) {
                            case SUCCESS:
                                break;
                            case ER_USER_EXISTS:
                                System.out.println("User already exists in database");
                                break;
                        }
                    } while (true);
                    break;
                case 3: //Anon Search
                    searchMenu();
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

    int mainMenu() {
        if (this.username != null) {
            try {
                server.unregister(this.username);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        int choice = 0;
        do {
            System.out.println();
            System.out.println("----------------------------------");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Anonymous search");
            System.out.println("0. Exit");
            System.out.println("----------------------------------");
            System.out.print("Choose an option: ");
            while (true) {
                try {
                    choice = Integer.parseInt(sc.nextLine());
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("Not valid choice! ");
                    System.out.print("Choose an option: ");
                    continue;
                }
            }
            if ((choice < 0 || choice > 3))
                System.out.println("Not valid choice: " + choice);
        } while (choice < 0 || choice > 3);
        return choice;
    }

    int loggedUserMenu() {
        int choice = 0;
        do {
            while (true) {
                System.out.println();
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
                try {
                    choice = Integer.parseInt(sc.nextLine());
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("Not valid choice! ");
                    System.out.print("Choose an option: ");
                    continue;
                }
            }
            if ((this.isAdmin && (choice >= 0 && choice <= 7)) || (choice >= 0 || choice <= 3))
                break;
            System.out.println("Not valid choice: " + choice);

        } while (true);
        return choice;
    }

    void loggedUser() {
        int choice = 1;
        while (choice != 0) {
            choice = loggedUserMenu();
            switch (choice) {
                case 1: //search
                    searchMenu();
                    break;
                case 2: //history
                    printUserHistory();
                    break;
                case 3: //consult Hyperlink pages
                    loggedUserHyperlink();
                    break;
                case 4: //give permission
                    givePermissions();
                    break;
                case 5: //list users

                    break;
                case 6: //index Url
                    indexURL();
                    break;
                case 7: //system info
                    systemInfo();
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

    String untilNotEmpty(String ask, String repifnull) {
        String st = "";
        do {
            System.out.print(ask);
            st = sc.nextLine();
            if (st.isEmpty())
                System.out.println(repifnull);
        } while (st.isEmpty());

        return st;
    }


    void loggedUserHyperlink() {

        String url = untilNotEmpty("Type in the URL: ", "URL can't be empty");

        ArrayList<String> links = null;
        try {
            links = server.getHyperLinks(url);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        System.out.println("Links to this page");
        for (String link : links) {
            System.out.println(link);
        }

    }

    void givePermissions() {
        String username = untilNotEmpty("Type a username of a user you want to turn into an admin:", "Usarname cannot be empty");
        try {
            PacketBuilder.RESULT result = server.grantAdmin(this, username);
            switch (result) {
                case SUCCESS:
                    System.out.println("User " + username + " is now an admin.");
                    break;
                case ER_NO_USER:
                    System.out.println("No user with username: " + username);
                    break;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    void indexURL() {
        String url = untilNotEmpty("URL: ", "Not able to index empty URL");
        try {
            PacketBuilder.RESULT result = server.indexRequest(url);
            if (result.equals(PacketBuilder.RESULT.SUCCESS)) {
                System.out.println("URL indexed");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void clearConsole() {
        for (int i = 0; i < 100; i++) {
            System.out.println();
        }
    }

    void systemInfo() {
        try {
            server.adminInPage(this.username);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        sc.nextLine();
        try {
            server.adminOutPage(this.username);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void searchMenu() {
        String termos = untilNotEmpty("Type in the terms you want to search:", "Not possible to search for empty word!");
        String[] words = termos.split(" ");
        String[] results = new String[0];
        try {
            results = server.search(this, words, this.username, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        System.out.println("Search results:");
        for (String result : results)
            if (result != null) {
                System.out.println("--------------------------------------");
                System.out.println(result);
            }
        System.out.print("Press enter to exit");
        String s = sc.nextLine();
        //TODO fazer com que possa se navegar atravez das paginas
    }
}