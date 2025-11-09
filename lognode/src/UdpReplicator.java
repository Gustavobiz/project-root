import java.net.*;
import java.nio.charset.StandardCharsets;

public class UdpReplicator {
    public static boolean sendAndAck(String url, String json, int timeoutMs) throws Exception {
        // url: udp://host:port
        String[] hp = url.substring("udp://".length()).split(":", 2);
        String host = hp[0]; int port = Integer.parseInt(hp[1]);

        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setSoTimeout(timeoutMs);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(host), port);
            ds.send(p);

            byte[] buf = new byte[1024];
            DatagramPacket ack = new DatagramPacket(buf, buf.length);
            ds.receive(ack);
            String resp = new String(ack.getData(), 0, ack.getLength(), StandardCharsets.UTF_8);
            return resp.contains("\"success\":true");
        }
    }
}
