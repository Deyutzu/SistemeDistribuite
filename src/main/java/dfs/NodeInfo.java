package dfs;

/**
 * Informațiile reținute de coordonator despre un nod din cluster.
 */
public class NodeInfo {
    private final String id;
    private final String address;
    private final int port;
    private NodeState state;
    private long lastHeartbeatMs;

    public NodeInfo(String id, String address, int port, NodeState state, long lastHeartbeatMs) {
        this.id = id;
        this.address = address;
        this.port = port;
        this.state = state;
        this.lastHeartbeatMs = lastHeartbeatMs;
    }

    public String getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public NodeState getState() {
        return state;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public long getLastHeartbeatMs() {
        return lastHeartbeatMs;
    }

    public void setLastHeartbeatMs(long lastHeartbeatMs) {
        this.lastHeartbeatMs = lastHeartbeatMs;
    }
}
