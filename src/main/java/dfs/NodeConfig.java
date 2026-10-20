package dfs;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configurația de pornire pentru un nod din sistem.
 */
public class NodeConfig {
    private final String id;
    private final int port;
    private final String dirPath;
    private final boolean coordinator;

    public NodeConfig(String id, int port, String dirPath, boolean coordinator) {
        this.id = id;
        this.port = port;
        this.dirPath = dirPath;
        this.coordinator = coordinator;
    }

    public String getId() {
        return id;
    }

    public int getPort() {
        return port;
    }

    public String getDirPath() {
        return dirPath;
    }

    public Path getStorageDirectory() {
        return Paths.get(dirPath);
    }

    public boolean isCoordinator() {
        return coordinator;
    }

    public String getRoleName() {
        return coordinator ? "coordonator" : "nod de stocare";
    }
}
