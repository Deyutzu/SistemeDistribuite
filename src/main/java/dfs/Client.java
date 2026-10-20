package dfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Client în linie de comandă pentru interacțiunea cu sistemul de stocare.
 */
public class Client {
    private final String coordinatorHost;
    private final int coordinatorPort;

    public Client(String coordinatorHost, int coordinatorPort) {
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
    }

    public void runInteractiveLoop() throws IOException {
        System.out.printf("[client] coordonator configurat: %s:%d; comenzi: put, get, ls, rm, nodes, exit%n",
                coordinatorHost, coordinatorPort);
        System.out.flush();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "exit":
                        return;
                    case "put":
                    case "get":
                    case "ls":
                    case "rm":
                    case "nodes":
                        System.out.println("[client] operația '" + command + "' nu este implementată încă.");
                        break;
                    default:
                        System.out.println("[client] comandă necunoscută: " + command
                                + ". Comenzi permise: put, get, ls, rm, nodes, exit");
                        break;
                }
            }
        }
    }

    private static Client parseArgs(String[] args) {
        String coordinatorValue = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--coordinator".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --coordinator.");
                }
                coordinatorValue = args[++i].trim();
            } else {
                throw new IllegalArgumentException("Argument necunoscut: " + arg);
            }
        }

        if (coordinatorValue == null || coordinatorValue.isEmpty()) {
            throw new IllegalArgumentException("Argumentul obligatoriu --coordinator <host:port> lipsește.");
        }

        int colonIndex = coordinatorValue.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == coordinatorValue.length() - 1) {
            throw new IllegalArgumentException(
                    "Format invalid pentru --coordinator: '" + coordinatorValue + "'. Format așteptat: host:port.");
        }

        String host = coordinatorValue.substring(0, colonIndex).trim();
        String portStr = coordinatorValue.substring(colonIndex + 1).trim();

        if (host.isEmpty()) {
            throw new IllegalArgumentException("Adresa coordonatorului nu poate fi vidă.");
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port invalid pentru coordonator: '" + portStr + "'.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Portul coordonatorului trebuie să fie între 1 și 65535.");
        }

        return new Client(host, port);
    }

    public static void main(String[] args) {
        Client client;
        try {
            client = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Eroare la argumente: " + e.getMessage());
            System.err.println("Utilizare: Client --coordinator <host:port>");
            System.exit(1);
            return;
        }

        try {
            client.runInteractiveLoop();
        } catch (IOException e) {
            System.err.println("Eroare la citirea comenzilor: " + e.getMessage());
            System.exit(1);
        }
    }
}
