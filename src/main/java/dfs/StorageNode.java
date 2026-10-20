package dfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Procesul unui nod din sistemul de stocare distribuită.
 */
public class StorageNode {
    private final NodeConfig config;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    public StorageNode(NodeConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        Path storageDir = config.getStorageDirectory();
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        System.out.printf("[%s] pornit: port %d, director %s, rol: %s%n",
                config.getId(),
                config.getPort(),
                config.getDirPath(),
                config.getRoleName());
        System.out.flush();

        Runtime.getRuntime().addShutdownHook(new Thread(stopLatch::countDown));
    }

    public void awaitTermination() throws InterruptedException {
        stopLatch.await();
    }

    public static void main(String[] args) {
        NodeConfig config;
        try {
            config = NodeArgsParser.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Eroare la argumente: " + e.getMessage());
            System.err.println("Utilizare: StorageNode --id <id> --port <port> --dir <director> [--coordinator]");
            System.exit(1);
            return;
        }

        StorageNode node = new StorageNode(config);
        try {
            node.start();
            node.awaitTermination();
        } catch (IOException e) {
            System.err.println("Nu s-a putut crea directorul de stocare: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
