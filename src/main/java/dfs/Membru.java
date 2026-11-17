package dfs;

import java.net.InetAddress;

/**
 * Datele unui nod descoperit prin multicast.
 */
public class Membru {
    private final String id;
    private final InetAddress address;
    private final int portTcp;
    private volatile long lastAnnouncementMs;

    public Membru(String id, InetAddress address, int portTcp, long lastAnnouncementMs) {
        this.id = id;
        this.address = address;
        this.portTcp = portTcp;
        this.lastAnnouncementMs = lastAnnouncementMs;
    }

    public String getId() {
        return id;
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getHostAddress() {
        return address.getHostAddress();
    }

    public int getPortTcp() {
        return portTcp;
    }

    public long getLastAnnouncementMs() {
        return lastAnnouncementMs;
    }

    public void setLastAnnouncementMs(long lastAnnouncementMs) {
        this.lastAnnouncementMs = lastAnnouncementMs;
    }

    @Override
    public String toString() {
        return id + "@" + address.getHostAddress() + ":" + portTcp;
    }
}
