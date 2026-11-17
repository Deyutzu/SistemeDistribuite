package dfs;

import dfs.common.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Procesul unui nod din sistemul de stocare distribuită.
 */
public class StorageNode {
    private final NodeConfig config;
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService connectionPool;
    private MulticastDiscovery discovery;
    private Thread acceptThread;

    public StorageNode(NodeConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        Path storageDir = config.getStorageDirectory();
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        running = true;
        serverSocket = new ServerSocket(config.getPort());
        connectionPool = Executors.newCachedThreadPool();

        System.out.printf("[%s] pornit: port %d, director %s, rol: %s%n",
                config.getId(),
                config.getPort(),
                config.getDirPath(),
                config.getRoleName());
        System.out.printf("[%s] grup multicast: %s:%d%n",
                config.getId(),
                config.getMulticastGroup(),
                Protocol.MULTICAST_PORT);
        System.out.flush();

        discovery = new MulticastDiscovery(config.getId(), config.getPort(), config.getTeam());
        discovery.start();

        acceptThread = new Thread(this::acceptLoop, "tcp-accept-" + config.getId());
        acceptThread.setDaemon(true);
        acceptThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionPool.submit(() -> handleConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[" + config.getId() + "] eroare la acceptarea conexiunii TCP: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (Socket s = socket;
             DataInputStream in = new DataInputStream(s.getInputStream());
             DataOutputStream out = new DataOutputStream(s.getOutputStream())) {
            String[] header = Protocol.readHeader(in);
            if (header.length == 0) {
                Protocol.writeHeader(out, "ERROR comanda necunoscută");
                return;
            }

            if ("PUT".equals(header[0])) {
                handlePut(header, in, out);
            } else if ("GET".equals(header[0])) {
                handleGet(header, out);
            } else {
                Protocol.writeHeader(out, "ERROR comanda necunoscută");
            }
        } catch (Exception e) {
            System.err.println("[" + config.getId() + "] eroare pe conexiunea TCP: " + e.getMessage());
        }
    }

    private void handlePut(String[] header, DataInputStream in, DataOutputStream out) throws IOException {
        if (header.length != 4) {
            Protocol.writeHeader(out, "ERROR comanda necunoscută");
            return;
        }

        String fileName = header[1];
        if (!Protocol.isValidFileName(fileName) || fileName.endsWith(".part")) {
            Protocol.writeHeader(out, "ERROR nume invalid");
            return;
        }

        long size;
        try {
            size = Long.parseLong(header[2]);
        } catch (NumberFormatException e) {
            Protocol.writeHeader(out, "ERROR comanda necunoscută");
            return;
        }
        String expectedSha256 = header[3];

        if (size < 0) {
            Protocol.writeHeader(out, "ERROR comanda necunoscută");
            return;
        }
        if (size > Protocol.MAX_FILE_SIZE) {
            Protocol.writeHeader(out, "ERROR fișier prea mare");
            return;
        }

        Path storageDir = config.getStorageDirectory();
        Path partPath = storageDir.resolve(fileName + ".part");
        Path targetPath = storageDir.resolve(fileName);

        try {
            String actualSha256 = Protocol.receiveBytes(in, size, partPath);
            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                Files.deleteIfExists(partPath);
                Protocol.writeHeader(out, "ERROR checksum diferit");
                return;
            }
            Files.move(partPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Protocol.writeHeader(out, "STORED " + actualSha256);
        } catch (IOException e) {
            Files.deleteIfExists(partPath);
            throw e;
        }
    }

    private void handleGet(String[] header, DataOutputStream out) throws IOException {
        if (header.length != 2) {
            Protocol.writeHeader(out, "ERROR comanda necunoscută");
            return;
        }

        String fileName = header[1];
        if (!Protocol.isValidFileName(fileName)) {
            Protocol.writeHeader(out, "ERROR nume invalid");
            return;
        }
        if (fileName.endsWith(".part")) {
            Protocol.writeHeader(out, "NOT_FOUND");
            return;
        }

        Path filePath = config.getStorageDirectory().resolve(fileName);
        if (!Files.isRegularFile(filePath)) {
            Protocol.writeHeader(out, "NOT_FOUND");
            return;
        }

        long size = Files.size(filePath);
        String sha256 = Protocol.checksum(filePath);
        Protocol.writeHeader(out, "FILE " + size + " " + sha256);
        Protocol.sendBytes(out, filePath);
    }

    public void stop() {
        running = false;
        if (discovery != null) {
            discovery.stop();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (connectionPool != null) {
            connectionPool.shutdownNow();
        }
        stopLatch.countDown();
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
            System.err.println("Utilizare: StorageNode --id <id> --port <port> --dir <director> --echipa <k> [--coordinator]");
            System.exit(1);
            return;
        }

        StorageNode node = new StorageNode(config);
        try {
            node.start();
            node.awaitTermination();
        } catch (IOException e) {
            System.err.println("Eroare la pornirea nodului: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
