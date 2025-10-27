import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Scanner;

public class HeartbeatTask implements Runnable {
    private final Discovery discovery;
    private final long intervalMs;
    private final int retries;

    public HeartbeatTask(Discovery discovery, long intervalMs, int retries) {
        this.discovery = discovery;
        this.intervalMs = intervalMs;
        this.retries = retries;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Collection<NodeInfo> nodes = discovery.list(); // usa o list() do Discovery
                for (NodeInfo n : nodes) {
                    Health h = pingHealth(n);
                    if (h.ok) {
                        // atualiza usando o ID que já está cadastrado no Discovery
                        discovery.updateFromHealth(
                                n.nodeId,
                                h.status != null ? h.status : "UP",
                                h.term,
                                h.commitIndex
                        );
                    } else {
                        discovery.markDown(n.nodeId);
                    }
                }
                Thread.sleep(intervalMs);
            } catch (InterruptedException ie) {
                return; // encerra a thread
            } catch (Exception ignore) {
                // continua o loop
            }
        }
    }

    // Faz GET /health, tenta algumas vezes e retorna dados parseados
    private Health pingHealth(NodeInfo n) {
        for (int i = 0; i < retries; i++) {
            try {
                URL url = new URL(n.baseUrl() + "/health");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(1000);
                con.setReadTimeout(1000);
                con.setRequestMethod("GET");
                int code = con.getResponseCode();
                if (code == 200) {
                    String json;
                    try (Scanner s = new Scanner(con.getInputStream(), StandardCharsets.UTF_8)) {
                        s.useDelimiter("\\A");
                        json = s.hasNext() ? s.next() : "";
                    }
                    Health h = new Health();
                    h.ok = true;
                    h.status = HttpUtils.extractString(json, "status");
                    Integer term = HttpUtils.extractInt(json, "term");
                    Integer commit = HttpUtils.extractInt(json, "commitIndex");
                    h.term = term != null ? term : 0;
                    h.commitIndex = commit != null ? commit : 0;
                    return h;
                }
            } catch (Exception ignore) { /* tenta de novo */ }
        }
        return Health.down();
    }

    private static class Health {
        boolean ok;
        String status;
        int term;
        int commitIndex;

        static Health down() {
            Health h = new Health();
            h.ok = false;
            h.status = "DOWN";
            h.term = 0;
            h.commitIndex = 0;
            return h;
        }
    }
}
