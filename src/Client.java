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
    private String nodeIP;    // This client's IP
    private String serverIP;  // Server's IP from config
    private int serverPort;   // Server's port from config
    private SecureRandom secureRandom;
    private ScheduledExecutorService scheduler;
    private int ranNum1;
    private byte version;

    public Client() {
        this.secureRandom = new SecureRandom();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.ranNum1 = secureRandom.nextInt(31);
        this.version = 1;
        loadConfig(); // Sets nodeIP, serverIP, serverPort
    }

    private void loadConfig() {
        String configFile = "client.config";
        try {
            // Get this client's IP
            this.nodeIP = InetAddress.getLocalHost().getHostAddress();

            // Read server IP and port from config
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String[] parts = line.trim().split(":");
                    if (parts.length == 2) {
                        this.serverIP = parts[0];
                        this.serverPort = Integer.parseInt(parts[1]);
                    } else {
                        throw new IOException("Invalid server address format in config");
                    }
                } else {
                    throw new IOException("Server address not found in config");
                }
            } catch (IOException e) {
                System.out.println("Error reading config: " + e.getMessage());
                this.serverIP = "127.0.0.1";
                this.serverPort = 7000;
            }
        } catch (Exception e) {
            System.out.println("Could not determine local IP: " + e.getMessage());
            this.nodeIP = "127.0.0.1";
            this.serverIP = "127.0.0.1";
            this.serverPort = 7000;
        }
    }

    public void startHeartbeatTimer() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
                ranNum1 = secureRandom.nextInt(31);
                scheduler.schedule(this, ranNum1, TimeUnit.SECONDS);
            }
        };
        scheduler.schedule(task, ranNum1, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            DatagramSocket socket = new DatagramSocket();
            int timestamp = (int) (System.currentTimeMillis() / 1000);
            String fileListing = Message.getCurrentFileListing();
            Message heartbeat = new Message(version, nodeIP, timestamp, fileListing);
            byte[] byteMessage = heartbeat.getMessageBytes();

            InetAddress IPaddress = InetAddress.getByName(serverIP);
            DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, IPaddress, serverPort);
            socket.send(packet);
            System.out.println("Sent heartbeat to " + serverIP + ":" + serverPort + " at Unix time " + timestamp + "\n");
            socket.close();
            version++;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client thisPC = new Client();
        thisPC.startHeartbeatTimer();
    }
}