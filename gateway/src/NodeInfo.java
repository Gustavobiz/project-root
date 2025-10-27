import java.time.Instant;

public class NodeInfo {
    public String nodeId;
    public String ip;
    public int port;
    public String role;          // leader | follower
    public String transport;     // http | udp | grpc
    public String status = "UNKNOWN"; // UP | DOWN | UNKNOWN (usado pelo gateway/heartbeat)
    public int commitIndex = 0;
    public int term = 0;
    public Instant lastSeen = Instant.now();

    // Compatibilidade com o seu Discovery 
    public boolean online = false;

    public NodeInfo() {}

    public NodeInfo(String nodeId, String ip, int port, String role, String transport) {
        this.nodeId = nodeId;
        this.ip = ip;
        this.port = port;
        this.role = role;
        this.transport = transport;
        this.status = "UNKNOWN";
        this.lastSeen = Instant.now();
        this.online = false;
    }

    public String baseUrl() {
        return "http://" + ip + ":" + port;
    }

    // Compatível com seu Discovery.toJsonArray()
    public String toJson() {
        return String.format(
            "{\"nodeId\":\"%s\",\"ip\":\"%s\",\"port\":%d,\"role\":\"%s\",\"transport\":\"%s\",\"status\":\"%s\",\"term\":%d,\"commitIndex\":%d,\"lastSeen\":\"%s\",\"online\":%s}",
            nodeId, ip, port, role, transport, status, term, commitIndex, lastSeen.toString(), online ? "true" : "false"
        );
    }
}
