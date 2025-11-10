import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class HeartbeatTask implements Runnable {
    private final Discovery discovery;
    private final long intervalMs;
    private final int retries;

    // backoff para evitar promover varias vezes seguidas
    private long lastElectionTs = 0;
    private final long electionBackoffMs = 3000; // 3s

    public HeartbeatTask(Discovery discovery, long intervalMs, int retries) {
        this.discovery = discovery;
        this.intervalMs = intervalMs;
        this.retries = retries;
    }

   @Override
public void run() {
    while (true) {
        try {
            // 1) Atualiza status via /health
            for (NodeInfo n : discovery.list()) {
                Health h = pingHealth(n);
                if (h.ok) discovery.updateFromHealth(n.nodeId, h.status != null ? h.status : "UP", h.term, h.commitIndex);
                else discovery.markDown(n.nodeId);
            }

            resolveSplitBrain();   // 1º: se há >1 líder, mantém só o determinístico
            maybeElectLeader();    // 2º: se não há líder UP, promove 1

            Thread.sleep(intervalMs);
        } catch (InterruptedException ie) {
            return;
        } catch (Exception ignore) {}
    }
}

    // -- Eleição automatica --
    private void maybeElectLeader() {
        List<NodeInfo> ups = discovery.list().stream()
                .filter(n -> "UP".equalsIgnoreCase(n.status))
                .collect(Collectors.toList());

        // ja existe um líder UP?
        boolean hasLeader = ups.stream().anyMatch(n -> "leader".equalsIgnoreCase(n.role));
        if (hasLeader) return;

        long now = System.currentTimeMillis();
        if (now - lastElectionTs < electionBackoffMs) return; // respeita backoff

        if (ups.isEmpty()) return; // ninguém UP

        // escolha determinística: menor nodeId (lexicográfica) vira líder
        ups.sort(Comparator.comparing(n -> n.nodeId));
        NodeInfo cand = ups.get(0);

        try {
            String url = "http://" + cand.ip + ":" + cand.port + "/becomeLeader";
            GatewayNet.postJson(url, "{}", 1500);
            cand.role = "leader";
            System.out.println("[election] Promoted " + cand.nodeId + " as LEADER");
        } catch (Exception e) {
            System.out.println("[election] Failed to promote " + cand.nodeId + ": " + e.getMessage());
        }
        lastElectionTs = now;
    }

    // Se houver mais de um líder UP, mantém 1 e rebaixa os demais
    private void resolveSplitBrain() {
        List<NodeInfo> leaders = discovery.list().stream()
                .filter(n -> "UP".equalsIgnoreCase(n.status) && "leader".equalsIgnoreCase(n.role))
                .collect(Collectors.toList());

        if (leaders.size() <= 1) return;

        // mantem o de menor nodeId como líder
        leaders.sort(Comparator.comparing(n -> n.nodeId));
        NodeInfo keeper = leaders.get(0);

        for (int i = 1; i < leaders.size(); i++) {
            NodeInfo demote = leaders.get(i);
            try {
                String url = "http://" + demote.ip + ":" + demote.port + "/becomeFollower";
                GatewayNet.postJson(url, "{}", 1500);
                demote.role = "follower";
                System.out.println("[split-brain] Demoted " + demote.nodeId + " to FOLLOWER");
            } catch (Exception e) {
                System.out.println("[split-brain] Failed to demote " + demote.nodeId + ": " + e.getMessage());
            }
        }
        // garante o papel do guardado
        keeper.role = "leader";
    }

    // Ping /health 
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
            } catch (Exception ignore) { }
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
