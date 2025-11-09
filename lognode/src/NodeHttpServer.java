import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class NodeHttpServer {
    private final NodeConfig cfg;
    private final LogCore core;
    private HttpServer server;

    public NodeHttpServer(NodeConfig cfg, LogCore core) {
        this.cfg = cfg;
        this.core = core;
    }

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(cfg.port), 0);

        // /health -> status simples em JSON
        server.createContext("/health", new HttpHandler() {
            @Override public void handle(HttpExchange ex) {
                try {
                    String json = String.format(
                        "{\"status\":\"UP\",\"nodeId\":\"%s\",\"role\":\"%s\",\"term\":%d,\"commitIndex\":%d}",
                        cfg.nodeId,
                        core.getRoleString(cfg.isLeader()),
                        core.getTerm(),
                        core.getCommitIndex()
                    );
                    sendJson(ex, 200, json);
                } catch (Exception e) {
                    safeFail(ex, e);
                }
            }
        });

        // /read?key=a  -> retorna o valor aplicado no estado
server.createContext("/read", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String query = ex.getRequestURI().getRawQuery();
            String key = null;
            if (query != null) {
                for (String p : query.split("&")) {
                    String[] kv = p.split("=", 2);
                    if (kv.length == 2 && "key".equals(kv[0])) {
                        key = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            if (key == null || key.isBlank()) {
                sendJson(ex, 400, "{\"error\":\"missing key\"}");
                return;
            }
            String value;
            synchronized (core) {
                value = core.get(key);
            }
            String resp = (value == null)
                ? String.format("{\"key\":\"%s\",\"found\":false}", escape(key))
                : String.format("{\"key\":\"%s\",\"found\":true,\"value\":\"%s\"}", escape(key), escape(value));
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});

// /replicate (followers recebem do líder)
server.createContext("/replicate", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                safeMethodNotAllowed(ex); return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            int index = extractInt(body, "index");
            int term  = extractInt(body, "term");
            String cmd = extractString(body, "command");
            int leaderCommit = extractInt(body, "leaderCommit"); // NOVO

            if (index <= 0 || cmd == null) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"invalid fields\"}");
                return;
            }

            synchronized (core) {
                core.append(new LogEntry(index, term, cmd));
                if (leaderCommit > 0) {
                    core.commitTo(leaderCommit); // aplica no KV até o índice confirmado
                }
            }

            String resp = String.format("{\"success\":true,\"matchIndex\":%d}", index);
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendJson(ex, 500, "{\"success\":false,\"error\":\""+e.getMessage()+"\"}");
        }
    }
});

// /append: apenas o lider recebe do gateway; replica para followers (HTTP/UDP/TCP)
server.createContext("/append", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                safeMethodNotAllowed(ex); return;
            }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String cmd = extractString(body, "command");
            String followers = extractString(body, "followers"); // ";" separados

            if (!cfg.isLeader()) { sendJson(ex, 409, "{\"error\":\"not leader\"}"); return; }
            if (cmd == null)     { sendJson(ex, 400, "{\"error\":\"missing command\"}"); return; }

            int idx, termNow;
            synchronized (core) {
                idx = core.nextIndex();
                termNow = core.getTerm();
                core.append(new LogEntry(idx, termNow, cmd));
            }

            // --- ÚNICO LOOP DE REPLICAÇÃO (HTTP/UDP/TCP) ---
            int followerAcks = 0, followersCount = 0;
            if (followers != null && !followers.isBlank()) {
                String[] arr = followers.split(";");
                followersCount = arr.length;

                String payload = String.format(
                    "{\"index\":%d,\"term\":%d,\"command\":\"%s\",\"leaderCommit\":%d}",
                    idx, termNow, escape(cmd), idx
                );

                for (String f : arr) {
                    f = f.trim();
                    if (f.isEmpty()) continue;
                    try {
                        boolean ok;
                        if (f.startsWith("udp://")) {
                            ok = UdpReplicator.sendAndAck(f, payload, 1000);
                        } else if (f.startsWith("tcp://")) {
                            ok = TcpReplicator.sendAndAck(f, payload, 1500);
                        } else {
                            // HTTP padrão: http://host:port/replicate
                            String url = f + "/replicate";
                            String resp = NodeNet.postJson(url, payload, 1500);
                            ok = (resp != null && resp.contains("\"success\":true"));
                        }
                        if (ok) followerAcks++;
                    } catch (Exception ignore) { /* follower falhou */ }
                }
            }

            int totalNodes = 1 + followersCount;         // líder + followers
            int totalAcks  = 1 + followerAcks;           // líder conta como ack
            int majority   = (totalNodes / 2) + 1;

            boolean committed = totalAcks >= majority;
            if (committed) {
                synchronized (core) { core.commitTo(idx); } // aplica no KV do líder
            }

            String resp = String.format(
                "{\"index\":%d,\"committed\":%s,\"acks\":%d,\"needed\":%d}",
                idx, committed ? "true" : "false", totalAcks, majority
            );
            sendJson(ex, 200, resp);

        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});


//troca role em tempo de execução 
// POST /becomeLeader  promove este no a lider
server.createContext("/becomeLeader", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex,405,"{\"error\":\"method not allowed\"}"); return; }
            cfg.setRole("leader");
            sendJson(ex, 200, "{\"ok\":true,\"role\":\"leader\"}");
            System.out.println(">>> PROMOTED to LEADER (port="+cfg.port+", id="+cfg.nodeId+")");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});

// POST /becomeFollower  força este no a follower
server.createContext("/becomeFollower", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex,405,"{\"error\":\"method not allowed\"}"); return; }
            cfg.setRole("follower");
            sendJson(ex, 200, "{\"ok\":true,\"role\":\"follower\"}");
            System.out.println(">>> DEMOTED to FOLLOWER (port="+cfg.port+", id="+cfg.nodeId+")");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});


        server.setExecutor(null); // default executor
        server.start();
        System.out.printf("LogNode HTTP on port %d (%s, id=%s)%n", cfg.port, cfg.role, cfg.nodeId);
    }

    private void safeFail(HttpExchange ex, Exception e) {
        try {
            String msg = "{\"status\":\"ERROR\",\"message\":\"" + escape(e.getMessage()) + "\"}";
            sendJson(ex, 500, msg);
        } catch (Exception ignore) {}
    }

    //utilitarios locais simples (sem libs) 
    private static void sendJson(HttpExchange ex, int code, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (Exception ignore) {}
    }

    private static void safeMethodNotAllowed(HttpExchange ex) {
        sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
    }

    private static String extractString(String json, String field) {
        String token = "\"" + field + "\"";
        int i = json.indexOf(token);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + token.length());
        if (colon < 0) return null;
        int first = json.indexOf('"', colon + 1);
        int second = json.indexOf('"', first + 1);
        if (first < 0 || second < 0) return null;
        return json.substring(first + 1, second);
    }

    private static int extractInt(String json, String field) {
        String token = "\"" + field + "\"";
        int i = json.indexOf(token);
        if (i < 0) return 0;
        int colon = json.indexOf(':', i + token.length());
        if (colon < 0) return 0;
        int end = json.indexOf(',', colon + 1);
        if (end < 0) end = json.indexOf('}', colon + 1);
        String num = json.substring(colon + 1, end).replace("\"","").trim();
        try { return Integer.parseInt(num); } catch(Exception e){ return 0; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }
}
