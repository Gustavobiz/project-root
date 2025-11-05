import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class GatewayHttpServer {
    private final Discovery discovery;
    private final int port;
    private HttpServer server;

    public GatewayHttpServer(Discovery discovery, int port) {
        this.discovery = discovery;
        this.port = port;
    }

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);

// POST /register  (aceita JSON OU x-www-form-urlencoded)
server.createContext("/register", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            String nodeId, ip, role, transport;
            int nodePort;

            if (contentType != null && contentType.toLowerCase().contains("application/json")) {
                // JSON
                String body = HttpUtils.readBody(ex);
                nodeId    = HttpUtils.extractString(body, "nodeId");
                ip        = HttpUtils.extractString(body, "ip");
                Integer p = HttpUtils.extractInt(body, "port");
                nodePort  = (p != null ? p : 0);
                role      = HttpUtils.extractString(body, "role");
                transport = HttpUtils.extractString(body, "transport");
            } else {
                // form/query
                Map<String,String> params = readParams(ex);
                nodeId    = params.get("nodeId");
                ip        = params.get("ip");
                nodePort  = params.containsKey("port") ? Integer.parseInt(params.get("port")) : 0;
                role      = params.get("role");
                transport = params.get("transport");
            }

            // validação simples
            if (nodeId == null || ip == null || nodePort <= 0) {
                respond(ex, 400, "{\"error\":\"missing nodeId, ip or port\"}");
                return;
            }
            if (role == null) role = "follower";
            if (transport == null) transport = "http";

            NodeInfo n = new NodeInfo();
            n.nodeId = nodeId;
            n.ip = ip;
            n.port = nodePort;
            n.role = role;
            n.transport = transport;
            n.status = "UNKNOWN";
            discovery.register(n);

            respond(ex, 200, "{\"status\":\"registered\",\"nodeId\":\""+nodeId+"\"}");
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});

// GET /log/read?key=a  -> encaminha para um nó UP (de preferência o líder)
server.createContext("/log/read", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtils.sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            // extrai key
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
                HttpUtils.sendJson(ex, 400, "{\"error\":\"missing key\"}");
                return;
            }

            // escolhe um nó UP (prioriza líder UP)
NodeInfo target = null;
for (NodeInfo n : discovery.list()) {
    if (!"UP".equalsIgnoreCase(n.status)) continue;
    if ("leader".equalsIgnoreCase(n.role)) { target = n; break; }
}
if (target == null) {
    HttpUtils.sendJson(ex, 503, "{\"error\":\"no leader available\"}");
    return;
}

            String url = "http://" + target.ip + ":" + target.port + "/read?key=" +
                    java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
            String resp = GatewayNet.httpGet(url, 1500); // vamos criar httpGet abaixo
            HttpUtils.sendJson(ex, 200, resp);
        } catch (Exception e) {
            HttpUtils.sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});


        // GET /healthTable
        server.createContext("/healthTable", new HttpHandler() {
            @Override public void handle(HttpExchange ex) {
                try {
                    String json = discovery.list().stream().map(n -> String.format(
                        "{\"nodeId\":\"%s\",\"ip\":\"%s\",\"port\":%d,\"role\":\"%s\",\"transport\":\"%s\",\"status\":\"%s\",\"term\":%d,\"commitIndex\":%d,\"lastSeen\":\"%s\"}",
                        n.nodeId, n.ip, n.port, n.role, n.transport, n.status, n.term, n.commitIndex, n.lastSeen.toString()
                    )).collect(Collectors.joining(",", "[", "]"));
                    respond(ex, 200, json);
                } catch (Exception e) {
                    respond(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
                }
            }
        });

        // POST /log/append  { "command":"PUT a=1" }
        server.createContext("/log/append", new HttpHandler() {
            @Override public void handle(HttpExchange ex) {
                try {
                    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                        HttpUtils.sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                        return;
                    }
                    String body = HttpUtils.readBody(ex);
                    String command = HttpUtils.extractString(body, "command");
                    if (command == null) {
                        HttpUtils.sendJson(ex, 400, "{\"error\":\"missing command\"}");
                        return;
                    }

                    // escolher líder UP
                    NodeInfo leader = null;
                    List<NodeInfo> followers = new ArrayList<>();
                    for (NodeInfo n : discovery.list()) {
                        if (!"UP".equalsIgnoreCase(n.status)) continue; // só nós UP
                        if ("leader".equalsIgnoreCase(n.role)) leader = n;
                    }
                    if (leader == null) {
                        HttpUtils.sendJson(ex, 503, "{\"error\":\"no leader available\"}");
                        return;
                    }
                    // seguidores UP
                    for (NodeInfo n : discovery.list()) {
                        if (!"UP".equalsIgnoreCase(n.status)) continue;
                        if ("follower".equalsIgnoreCase(n.role)) followers.add(n);
                    }

                    String followersStr = followers.stream()
                        .map(f -> "http://" + f.ip + ":" + f.port)
                        .collect(Collectors.joining(";"));

                    String payload = String.format(
                        "{\"command\":\"%s\",\"followers\":\"%s\"}",
                        escape(command), followersStr
                    );

                    String url = leader.baseUrl() + "/append";
                    String resp = GatewayNet.postJson(url, payload, 2000);
                    HttpUtils.sendJson(ex, 200, resp);
                } catch (Exception e) {
                    HttpUtils.sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
                }
            }
        });
        // POST  Gateway manda o follower virar líder (teste)
server.createContext("/admin/promote", new HttpHandler() {
    @Override public void handle(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtils.sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String q = ex.getRequestURI().getRawQuery();
            String nodeId = null;
            if (q != null) {
                for (String p : q.split("&")) {
                    String[] kv = p.split("=",2);
                    if (kv.length==2 && "nodeId".equals(kv[0])) {
                        nodeId = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            if (nodeId == null) {
                HttpUtils.sendJson(ex, 400, "{\"error\":\"missing nodeId\"}");
                return;
            }

            NodeInfo target = discovery.get(nodeId);
            if (target == null || !"UP".equalsIgnoreCase(target.status)) {
                HttpUtils.sendJson(ex, 404, "{\"error\":\"node not found or down\"}");
                return;
            }

            // manda o nó virar líder
            String url = "http://" + target.ip + ":" + target.port + "/becomeLeader";
            String resp = GatewayNet.postJson(url, "{}", 1500);

            // atualiza registro local para refletir papel novo
            target.role = "leader";
            HttpUtils.sendJson(ex, 200, "{\"ok\":true,\"nodeId\":\""+nodeId+"\",\"gatewayUpdate\":true,\"nodeResp\":"+resp+"}");
        } catch (Exception e) {
            HttpUtils.sendJson(ex, 500, "{\"error\":\""+e.getMessage()+"\"}");
        }
    }
});


        server.setExecutor(null);
        server.start();
        System.out.println("Gateway HTTP on port " + port);
    }

    //utilitários locais (dentro da classe, fora de métodos

    private static String escape(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }

    private static void respond(HttpExchange ex, int code, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (Exception ignore) {}
    }

    // lê parâmetros de query (?a=1&b=2) e também de corpo x-www-form-urlencoded
    private static Map<String,String> readParams(HttpExchange ex) throws Exception {
        Map<String,String> map = new HashMap<>();
        String query = ex.getRequestURI().getRawQuery();
        if (query != null) putParams(map, query);

        byte[] body = ex.getRequestBody().readAllBytes();
        if (body.length > 0) {
            String b = new String(body, StandardCharsets.UTF_8);
            putParams(map, b);
        }
        return map;
    }

    private static void putParams(Map<String,String> map, String s) throws Exception {
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length>1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k, v);
        }
    }
}
