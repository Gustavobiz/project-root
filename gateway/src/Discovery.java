import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Discovery {
    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();

    // ==== usado pelo Gateway (/register) ====
    public void register(NodeInfo n) {
        nodes.put(n.nodeId, n);
    }

    // ==== compatibilidade com Gateway/Heartbeat ====
    // Gateway e Heartbeat usam list()
    public Collection<NodeInfo> list() {
        return nodes.values();
    }

    // HeartbeatTask usa updateFromHealth e markDown
    public void updateFromHealth(String nodeId, String status, int term, int commitIndex) {
        NodeInfo n = nodes.get(nodeId);
        if (n != null) {
            n.status = status != null ? status : "UP";
            n.term = term;
            n.commitIndex = commitIndex;
            n.lastSeen = java.time.Instant.now();
            n.online = "UP".equalsIgnoreCase(n.status);
        }
    }

    public void markDown(String nodeId) {
        NodeInfo n = nodes.get(nodeId);
        if (n != null) {
            n.status = "DOWN";
            n.online = false;
        }
    }

    // ==== helpers extras que você adicionou ====
    public Collection<NodeInfo> all() {
        return nodes.values();
    }

    public NodeInfo get(String nodeId) {
        return nodes.get(nodeId);
    }

    public Optional<NodeInfo> currentLeader() {
        return nodes.values().stream()
                .filter(n -> "leader".equalsIgnoreCase(n.role) && n.online)
                .findFirst();
    }

    public String toJsonArray() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (NodeInfo n : nodes.values()) {
            if (!first) sb.append(",");
            sb.append(n.toJson());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
