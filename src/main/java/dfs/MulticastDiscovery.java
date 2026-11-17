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

    public MulticastDiscovery(String selfId, int selfPortTcp, int team) throws IOException {
        this.selfId = selfId;
        this.selfPortTcp = selfPortTcp;
        this.groupAddress = InetAddress.getByName(Protocol.multicastGroup(team));
        this.multicastPort = Protocol.MULTICAST_PORT;
    }

    public Map<String, Membru> getPeers() {
        return peers;
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
        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
        }
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
        }
    }
}
