import java.net.*;
import java.nio.charset.StandardCharsets;

public class GatewayUdpServer implements Runnable {
    private final int httpPort;

    public GatewayUdpServer(int httpPort) { this.httpPort = httpPort; }

    @Override public void run() {
        int udpPort = 8082; // default; pode ler de args se quiser
        try (DatagramSocket ds = new DatagramSocket(udpPort)) {
            System.out.println("Gateway UDP listening on " + udpPort);
            byte[] buf = new byte[4096];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ds.receive(p);
                String json = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);

                String reply;
                try {
                    String url = "http://localhost:" + httpPort + "/log/append";
                    reply = GatewayNet.postJson(url, json, 2000);
                } catch (Exception ex) {
                    reply = "{\"error\":\"" + ex.getMessage().replace("\"","'") + "\"}";
                }

                byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                DatagramPacket resp = new DatagramPacket(out, out.length, p.getAddress(), p.getPort());
                ds.send(resp);
            }
        } catch (Exception e) {
            System.err.println("GatewayUdpServer error: " + e.getMessage());
        }
    }
}
