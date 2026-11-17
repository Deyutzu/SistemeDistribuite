package dfs;

import dfs.common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Descoperirea nodurilor prin UDP multicast pe grupul echipei.
 */
public class MulticastDiscovery {
    private static final long ANNOUNCE_INTERVAL_MS = 2000L;
    private static final int MAX_PACKET_BYTES = 512;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String selfId;
    private final int selfPortTcp;
    private final InetAddress groupAddress;
    private final int multicastPort;
    private final ConcurrentHashMap<String, Membru> peers = new ConcurrentHashMap<>();

    private volatile boolean running;
    private MulticastSocket receiveSocket;
    private MulticastSocket sendSocket;
    private NetworkInterface sendInterface;
    private Thread senderThread;
    private Thread listenerThread;

    public MulticastDiscovery(String selfId, int selfPortTcp, int team) throws IOException {
        this.selfId = selfId;
        this.selfPortTcp = selfPortTcp;
        this.groupAddress = InetAddress.getByName(Protocol.multicastGroup(team));
        this.multicastPort = Protocol.MULTICAST_PORT;
    }

    public Map<String, Membru> getPeers() {
        return peers;
    }

    public void start() throws IOException {
        running = true;
        startListener();
        startSender();
    }

    public void startListener() throws IOException {
        running = true;
        List<NetworkInterface> joined = new ArrayList<>();
        receiveSocket = openJoinedSocket(groupAddress, multicastPort, joined);
        listenerThread = new Thread(this::listenLoop, "multicast-listener-" + selfId);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void startSender() throws IOException {
        running = true;
        List<NetworkInterface> interfaces = findMulticastInterfaces();
        sendSocket = new MulticastSocket();
        sendSocket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
        sendSocket.setTimeToLive(1);
        sendInterface = pickSendInterface(interfaces);
        if (sendInterface != null) {
            sendSocket.setOption(StandardSocketOptions.IP_MULTICAST_IF, sendInterface);
        }

        senderThread = new Thread(this::sendLoop, "multicast-sender-" + selfId);
        senderThread.setDaemon(true);
        senderThread.start();
    }

    private void sendLoop() {
        String payload = Protocol.HELLO_PREFIX + ";" + selfId + ";" + selfPortTcp;
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, multicastPort);
                sendSocket.send(packet);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[" + selfId + "] eroare la trimiterea anunțului multicast: " + e.getMessage());
                }
            }

            try {
                Thread.sleep(ANNOUNCE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[MAX_PACKET_BYTES];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                receiveSocket.receive(packet);
                Membru member = parseAnnouncement(packet);
                if (member == null || selfId.equals(member.getId())) {
                    continue;
                }
                Membru previous = peers.putIfAbsent(member.getId(), member);
                if (previous == null) {
                    String timestamp = LocalTime.now().format(TIME_FORMATTER);
                    int totalKnown = peers.size() + 1;
                    System.out.printf("%s [%s] nod descoperit: %s (cunosc acum %d)%n",
                            timestamp, selfId, member, totalKnown);
                    System.out.flush();
                } else {
                    previous.setLastAnnouncementMs(member.getLastAnnouncementMs());
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[" + selfId + "] eroare la recepția multicast: " + e.getMessage());
                }
            }
        }
    }

    public static Membru parseAnnouncement(DatagramPacket packet) {
        String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
        String[] parts = message.split(";", -1);
        if (parts.length != 3 || !Protocol.HELLO_PREFIX.equals(parts[0])) {
            return null;
        }
        String id = parts[1].trim();
        if (id.isEmpty()) {
            return null;
        }
        int portTcp;
        try {
            portTcp = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (portTcp < 1 || portTcp > 65535) {
            return null;
        }
        return new Membru(id, packet.getAddress(), portTcp, System.currentTimeMillis());
    }

    public static List<NetworkInterface> findMulticastInterfaces() throws SocketException {
        List<NetworkInterface> regular = new ArrayList<>();
        List<NetworkInterface> loopback = new ArrayList<>();
        Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
        while (all != null && all.hasMoreElements()) {
            NetworkInterface nif = all.nextElement();
            if (!nif.isUp() || !nif.supportsMulticast()) {
                continue;
            }
            if (!hasIpv4Address(nif)) {
                continue;
            }
            if (nif.isLoopback()) {
                loopback.add(nif);
            } else {
                regular.add(nif);
            }
        }
        regular.addAll(loopback);
        return regular;
    }

    private static boolean hasIpv4Address(NetworkInterface nif) {
        Enumeration<InetAddress> addrs = nif.getInetAddresses();
        while (addrs.hasMoreElements()) {
            if (addrs.nextElement() instanceof Inet4Address) {
                return true;
            }
        }
        return false;
    }

    public static MulticastSocket openJoinedSocket(InetAddress groupAddress, int port,
                                                   List<NetworkInterface> joinedOut) throws IOException {
        MulticastSocket socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);

        InetSocketAddress groupSocketAddr = new InetSocketAddress(groupAddress, port);
        List<NetworkInterface> candidates = findMulticastInterfaces();
        IOException lastError = null;

        for (NetworkInterface nif : candidates) {
            try {
                socket.joinGroup(groupSocketAddr, nif);
                if (joinedOut != null) {
                    joinedOut.add(nif);
                }
            } catch (IOException e) {
                lastError = e;
            }
        }

        if (joinedOut != null && joinedOut.isEmpty()) {
            try {
                socket.joinGroup(groupSocketAddr, null);
            } catch (IOException e) {
                socket.close();
                if (lastError != null) {
                    e.addSuppressed(lastError);
                }
                throw e;
            }
        }
        return socket;
    }

    private static NetworkInterface pickSendInterface(List<NetworkInterface> interfaces) {
        for (NetworkInterface nif : interfaces) {
            if (!nif.isLoopback()) {
                return nif;
            }
        }
        return interfaces.isEmpty() ? null : interfaces.get(0);
    }

    public void stop() {
        running = false;
        if (senderThread != null) {
            senderThread.interrupt();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
        }
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
        }
    }
}
