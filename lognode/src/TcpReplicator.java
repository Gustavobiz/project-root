import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpReplicator {
    public static boolean sendAndAck(String url, String json, int timeoutMs) throws Exception {
        // url: tcp://host:port
        String[] hp = url.substring("tcp://".length()).split(":", 2);
        String host = hp[0]; int port = Integer.parseInt(hp[1]);

        try (Socket s = new Socket(host, port)) {
            s.setSoTimeout(timeoutMs);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            bw.write(json); bw.write("\n"); bw.flush();
            String resp = br.readLine();
            return resp != null && resp.contains("\"success\":true");
        }
    }
}
