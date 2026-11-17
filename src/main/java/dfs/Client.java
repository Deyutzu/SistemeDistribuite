package dfs;

import dfs.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client în linie de comandă pentru interacțiunea cu sistemul de stocare.
 */
public class Client {
    private static final long DISCOVERY_LISTEN_MS = 3000L;
    private static final int SOCKET_POLL_TIMEOUT_MS = 300;
    private static final int MAX_PACKET_BYTES = 512;

    private final String coordinatorHost;
    private final int coordinatorPort;
    private final int team;
    private final Map<String, Membru> knownNodes = new ConcurrentHashMap<>();

    public Client(String coordinatorHost, int coordinatorPort, int team) {
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
        this.team = team;
    }

    public void runInteractiveLoop() throws IOException {
        if (coordinatorHost != null) {
            System.out.printf("[client] coordonator configurat: %s:%d, echipa %d (%s:%d); comenzi: put, get, ls, rm, nodes, exit%n",
                    coordinatorHost, coordinatorPort, team, Protocol.multicastGroup(team), Protocol.MULTICAST_PORT);
        } else {
            System.out.printf("[client] echipa %d (%s:%d); comenzi: put, get, ls, rm, nodes, exit%n",
                    team, Protocol.multicastGroup(team), Protocol.MULTICAST_PORT);
        }
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
                    case "nodes":
                        handleNodes();
                        break;
                    case "put":
                    case "get":
                    case "ls":
                    case "rm":
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

    private void handleNodes() {
        try {
            Map<String, Membru> heard = listenForNodes(DISCOVERY_LISTEN_MS, null);
            if (heard.isEmpty()) {
                System.out.println("Niciun nod activ detectat.");
                return;
            }
            for (Membru member : heard.values()) {
                System.out.println(member);
            }
        } catch (IOException e) {
            System.out.println("Eroare la ascultarea grupului multicast: " + e.getMessage());
        }
    }

    private Map<String, Membru> listenForNodes(long durationMs, String targetNodeId) throws IOException {
        InetAddress groupAddress = InetAddress.getByName(Protocol.multicastGroup(team));
        Map<String, Membru> heard = new TreeMap<>();
        byte[] buffer = new byte[MAX_PACKET_BYTES];

        try (MulticastSocket socket = MulticastDiscovery.openJoinedSocket(
                groupAddress, Protocol.MULTICAST_PORT, new ArrayList<>())) {
            socket.setSoTimeout(SOCKET_POLL_TIMEOUT_MS);
            long deadline = System.currentTimeMillis() + durationMs;

            while (System.currentTimeMillis() < deadline) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    Membru member = MulticastDiscovery.parseAnnouncement(packet);
                    if (member != null) {
                        heard.put(member.getId(), member);
                        knownNodes.put(member.getId(), member);
                        if (targetNodeId != null && targetNodeId.equals(member.getId())) {
                            break;
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                    // Continuăm până expiră fereastra de ascultare.
                }
            }
        }
        return heard;
    }

    private static Client parseArgs(String[] args) {
        String coordinatorValue = null;
        String teamStr = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--coordinator".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --coordinator.");
                }
                coordinatorValue = args[++i].trim();
            } else if ("--echipa".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --echipa.");
                }
                teamStr = args[++i].trim();
            } else {
                throw new IllegalArgumentException("Argument necunoscut: " + arg);
            }
        }

        if (teamStr == null || teamStr.isEmpty()) {
            throw new IllegalArgumentException("Argumentul obligatoriu --echipa <k> lipsește.");
        }

        int team;
        try {
            team = Integer.parseInt(teamStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Număr de echipă invalid: '" + teamStr + "'.");
        }
        if (team < 1 || team > 254) {
            throw new IllegalArgumentException("Numărul echipei trebuie să fie între 1 și 254.");
        }

        String host = null;
        int port = 0;
        if (coordinatorValue != null && !coordinatorValue.isEmpty()) {
            int colonIndex = coordinatorValue.lastIndexOf(':');
            if (colonIndex <= 0 || colonIndex == coordinatorValue.length() - 1) {
                throw new IllegalArgumentException(
                        "Format invalid pentru --coordinator: '" + coordinatorValue + "'. Format așteptat: host:port.");
            }

            host = coordinatorValue.substring(0, colonIndex).trim();
            String portStr = coordinatorValue.substring(colonIndex + 1).trim();

            if (host.isEmpty()) {
                throw new IllegalArgumentException("Adresa coordonatorului nu poate fi vidă.");
            }

            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port invalid pentru coordonator: '" + portStr + "'.");
            }

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Portul coordonatorului trebuie să fie între 1 și 65535.");
            }
        }

        return new Client(host, port, team);
    }

    public static void main(String[] args) {
        Client client;
        try {
            client = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Eroare la argumente: " + e.getMessage());
            System.err.println("Utilizare: Client --echipa <k> [--coordinator <host:port>]");
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
