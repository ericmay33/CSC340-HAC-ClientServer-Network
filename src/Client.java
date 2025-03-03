import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Client {
    private String nodeIP;  // This client's IP (from system)
    private int clientPort; // This client's port (from config)
    private String serverIP; // Server's IP (from config)
    private int serverPort;  // Server's port (from config)
    private SecureRandom secureRandom;
    private ScheduledExecutorService scheduler;
    private byte version;

    public Client() {
        try {
            this.nodeIP = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            throw new RuntimeException("Could not determine local IP: " + e.getMessage());
        }
        this.secureRandom = new SecureRandom();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.version = 1;
        loadConfig();
    }

    private void loadConfig() {
        String configFile = ".config";
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            boolean firstLine = true;
            boolean foundSelf = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String ip = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        if (firstLine) {
                            // First line is server
                            this.serverIP = ip;
                            this.serverPort = port;
                            firstLine = false;
                        }
                        // Check if this line matches the client's IP
                        if (ip.equals(nodeIP)) {
                            this.clientPort = port;
                            foundSelf = true;
                        }
                    } else {
                        throw new IOException("Invalid config format: expected IP:port");
                    }
                }
            }
            if (serverIP == null || serverPort == 0 || !foundSelf) {
                throw new IOException("Config missing server or client info for " + nodeIP);
            }
            System.out.println("Loaded config - Server: " + serverIP + ":" + serverPort + 
                              ", Client Port: " + clientPort);
        } catch (IOException e) {
            throw new RuntimeException("Error reading client config: " + e.getMessage());
        }
    }

    public void startHeartbeatTimer() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
                int delay = secureRandom.nextInt(31);
                scheduler.schedule(this, delay, TimeUnit.SECONDS);
            }
        };
        int initialDelay = secureRandom.nextInt(31);
        scheduler.schedule(task, initialDelay, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            DatagramSocket socket = new DatagramSocket();
            int timestamp = (int) (System.currentTimeMillis() / 1000);
            String fileListing = Message.getCurrentFileListing();
            Message heartbeat = new Message(version, nodeIP, timestamp, fileListing);
            byte[] byteMessage = heartbeat.getMessageBytes();

            InetAddress serverAddress = InetAddress.getByName(serverIP);
            DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, serverAddress, serverPort);
            socket.send(packet);
            System.out.println("Sent heartbeat to " + serverIP + ":" + serverPort + " at Unix time " + timestamp);
            socket.close();
            version++;
        } catch (Exception e) {
            System.err.println("Error sending heartbeat: " + e.getMessage());
        }
    }

    public void listenForServer() {
        Thread receiveThread = new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(clientPort);
                byte[] incomingData = new byte[5120];

                System.out.println("Client listening on " + nodeIP + ":" + clientPort);
                while (true) {
                    DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                    socket.receive(incomingPacket);
                    Message receivedMessage = Message.decode(incomingPacket.getData());
                    processAndPrint(receivedMessage);
                }
            } catch (Exception e) {
                System.err.println("Error in listening thread: " + e.getMessage());
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void processAndPrint(Message message) {
        String fileListing = message.getFileListing();
        System.out.println("Raw fileListing received: " + fileListing); // Debug log
        if (fileListing == null || fileListing.isEmpty()) {
            System.out.println("Received empty update from server");
            return;
        }

        String[] nodeEntries = fileListing.split(";");
        for (String entry : nodeEntries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                String nodeIP = parts[0];
                String status = parts[1];
                String files = parts[2];
                System.out.println("Node: " + nodeIP + ", Availability: " + status + ", Files: " + files);
            } else {
                System.out.println("Invalid entry format: " + entry);
            }
        }
    }

    public static void main(String[] args) {
        Client thisPC = new Client();
        thisPC.startHeartbeatTimer();
        thisPC.listenForServer();
    }
}