package dfs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Metadatele unui fișier stocat în sistem.
 */
public class FileMetadata {
    private final UUID fileId;
    private final String fileName;
    private final long size;
    private final String sha256Checksum;
    private final List<String> replicaNodeIds;

    public FileMetadata(UUID fileId, String fileName, long size, String sha256Checksum, List<String> replicaNodeIds) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.size = size;
        this.sha256Checksum = sha256Checksum;
        this.replicaNodeIds = new ArrayList<>(replicaNodeIds);
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public List<String> getReplicaNodeIds() {
        return Collections.unmodifiableList(replicaNodeIds);
    }
}
