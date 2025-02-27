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

    private String nodeIP;
    private final int clientPort = 7001;
    private String serverIP;
    private int serverPort;
    private SecureRandom secureRandom;
    private ScheduledExecutorService scheduler;
    private byte version;

    public Client() {
        try {
            this.nodeIP = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            this.nodeIP = "127.0.0.1";
            System.out.println("Could not determine local IP: " + e.getMessage());
        }
        this.secureRandom = new SecureRandom();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.version = 1;
        loadConfig();
    }

    private void loadConfig() {
        String configFile = ".config";
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line = reader.readLine().trim();
            String[] parts = line.split(":");
            this.serverIP = parts[0];
            this.serverPort = Integer.parseInt(parts[1]);
            System.out.println("Loaded config - Server: " + serverIP + ":" + serverPort);
        } catch (IOException e) {
            System.out.println("Error reading client config: " + e.getMessage());
            this.serverIP = "127.0.0.1";
            this.serverPort = 5050;
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
            e.printStackTrace();
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
                    processServerUpdate(receivedMessage);
                    printClientStatus(receivedMessage.getFileListing());
                }

            } catch (Exception e) {
                System.err.println("Error in listening thread: " + e.getMessage());
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void processServerUpdate(Message message) {
        // Empty for now;
    }

    private void printClientStatus(String data) {
        // Empty for now
    }

    public static void main(String[] args) {
        Client thisPC = new Client();
        thisPC.startHeartbeatTimer();
        thisPC.listenForServer();
    }
}