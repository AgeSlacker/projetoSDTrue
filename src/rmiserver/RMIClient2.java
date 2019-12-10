package rmiserver;

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

    static String rmiAddress;
    static int rmiPort;
    static String rmiLocation;

    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.out.println("Invalid number of arguments\nUsage: rmiserver.RMIClient rmiIP rmiPORT");
            }
            rmiAddress = args[0];
            rmiPort = Integer.parseInt(args[1]);
            rmiLocation = "//" + rmiAddress + ":" + rmiPort + "/RMIserver";
            new RMIClient2();
            //server.subscribe("client", client);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean rebindServer() {
        while (retries < 60) {
            try {
                this.server = (IServer) Naming.lookup("//localhost:7000/RMIserver");
                System.out.println("Waiting for server...");
                if (this.username != null) {
                    this.server.setLogged(this, username);
                }
                return true; // rebound successfully
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
        System.out.println("Servers must be down. Sorry for the inconvenience");
        return false;
    }

    protected RMIClient2() throws RemoteException {
        super();

        while (true) {
            try {
                this.server = (IServer) Naming.lookup(rmiLocation);
                break;
            } catch (NotBoundException e) {
                rebindServer();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        int choice1 = -1;
        while (choice1 != 0) {
            // rmiserver.User should not be logged in
            this.username = null;
            this.password = null;
            this.isAdmin = false; // MUDAMOS DEFESA
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
                        while (true) {
                            try {
                                answer = server.login(this, login, password);
                                break;
                            } catch (RemoteException e) {
                                rebindServer();
                            }
                        }
                        switch (answer) {
                            case SUCCESS:
                                this.username = login;
                                this.password = password;
                                loggedUser();
                                choice1 = -1;
                                break;
                            case ER_NO_USER:
                                System.out.println("rmiserver.User not in database");
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
                        System.out.println("--------Register rmiserver.Page------------");
                        System.out.println("Please insert your new credentials!(0 to cancel)");
                        String login = untilNotEmpty("Login:", "Login cannot be empty!");
                        if (login.equals("0"))
                            break;
                        String password = untilNotEmpty("Password: ", "Password cannot be empty!");
                        System.out.println("Consulting server...");
                        PacketBuilder.RESULT success = null;
                        while (true) {
                            try {
                                success = server.register(this, login, password);
                                break;
                            } catch (RemoteException e) {
                                rebindServer();
                            }
                        }
                        switch (success) {
                            case SUCCESS:
                                break;
                            case ER_USER_EXISTS:
                                System.out.println("rmiserver.User already exists in database");
                                break;
                        }
                        break;
                    } while (true);
                    break;
                case 3: //Anon rmiserver.Search
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
        while (true) {
            try {
                server.unregister(this.username);
                break;
            } catch (RemoteException e) {
                rebindServer();
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
                System.out.println("1. rmiserver.Search");
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
        ArrayList<Search> history;
        while (true) {
            try {
                history = server.getUserHistory(this, username);
                break;
            } catch (RemoteException e) {
                rebindServer();
            }
        }
        System.out.println("Your history:");
        for (Search s : history) {
            System.out.println("Date: " + s.date + " " + s.query);
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

        while (true) {
            try {
                links = server.getHyperLinks(url);
                break;
            } catch (RemoteException e) {
                rebindServer();
            }
        }

        System.out.println("Links to this page");
        for (String link : links) {
            System.out.println(link);
        }

    }

    void givePermissions() {
        String username = untilNotEmpty("Type a username of a user you want to turn into an admin:", "Usarname cannot be empty");
        PacketBuilder.RESULT result;
        while (true) {
            try {
                result = server.grantAdmin(this, username);
                break;
            } catch (RemoteException e) {
                rebindServer();
            }
        }

        switch (result) {
            case SUCCESS:
                System.out.println("rmiserver.User " + username + " is now an admin.");
                break;
            case ER_NO_USER:
                System.out.println("No user with username: " + username);
                break;
        }
    }

    void indexURL() {
        String url = untilNotEmpty("URL: ", "Not able to index empty URL");
        try {
            PacketBuilder.RESULT result = server.indexRequest(url);
            while (true) {
                try {
                    result = server.indexRequest(url);
                    break;
                } catch (RemoteException e) {
                    rebindServer();
                }
            }

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
        while (true) {
            try {
                server.adminInPage(this.username);
                break;
            } catch (RemoteException e) {
                rebindServer();
            }
        }
        sc.nextLine();
        while (true) {
            try {
                server.adminOutPage(this.username);
                break;
            } catch (RemoteException e) {
                rebindServer();
            }
        }
    }

    void searchMenu() {
        String termos = untilNotEmpty("Type in the terms you want to search:", "Not possible to search for empty word!");
        String[] words = termos.split(" ");
        ArrayList<Page> results = new ArrayList<>();
        while (true) {
            try {
                results = server.search(this, words, this.username, 0);
                break;
            } catch (RemoteException e) {
                rebindServer();
            }
        }
        System.out.println("rmiserver.Search results:");
        for (Page page : results)
            if (page != null) {
                System.out.println(page.name);
                System.out.println(page.url);
                System.out.println(page.description);
                System.out.println("--------------------------------------");
            }
        System.out.print("Press enter to exit");
        String s = sc.nextLine();
        //TODO fazer com que possa se navegar atravez das paginas
    }
}