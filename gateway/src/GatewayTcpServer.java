import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class GatewayTcpServer implements Runnable {
    private final int httpPort; // porta do gateway HTTP para onde vamos encaminhar

    public GatewayTcpServer(int httpPort) { this.httpPort = httpPort; }

    @Override public void run() {
        int tcpPort = 8081; // default; pode ler de args se quiser
        try (ServerSocket ss = new ServerSocket(tcpPort)) {
            System.out.println("Gateway TCP listening on " + tcpPort);
            while (true) {
                try (Socket s = ss.accept()) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

                    // Espera uma linha JSON
                    String line = br.readLine();
                    if (line == null || line.isBlank()) {
                        bw.write("{\"error\":\"empty payload\"}\n"); bw.flush(); continue;
                    }

                    // Encaminha para o proprio gateway HTTP 
                    String url = "http://localhost:" + httpPort + "/log/append";
                    String resp = GatewayNet.postJson(url, line, 2000);

                    bw.write(resp);
                    if (!resp.endsWith("\n")) bw.write("\n");
                    bw.flush();
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            System.err.println("GatewayTcpServer error: " + e.getMessage());
        }
    }
}
