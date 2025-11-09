public class NodeConfig {
    public String nodeId;
    public String ip = "localhost";   
    public int port;
    public String role;
    public String gateway;
    public String transport;

    // construtor vazio
    public NodeConfig() {}

    //  metodo auxiliar para criar a partir dos argumentos da linha de comando
    public static NodeConfig fromArgs(String[] args) {
        NodeConfig cfg = new NodeConfig();
        for (String a : args) {
            if (a.startsWith("--nodeId="))       cfg.nodeId = a.substring("--nodeId=".length());
            else if (a.startsWith("--ip="))      cfg.ip = a.substring("--ip=".length());   // 👈 novo
            else if (a.startsWith("--port="))    cfg.port = Integer.parseInt(a.substring("--port=".length()));
            else if (a.startsWith("--role="))    cfg.role = a.substring("--role=".length());
            else if (a.startsWith("--gateway=")) cfg.gateway = a.substring("--gateway=".length());
            else if (a.startsWith("--transport=")) cfg.transport = a.substring("--transport=".length());
        }

        // valores padrão caso algum argumento falte
        if (cfg.nodeId == null) cfg.nodeId = "node-" + System.currentTimeMillis();
        if (cfg.ip == null) cfg.ip = "localhost";
        if (cfg.role == null) cfg.role = "follower";
        if (cfg.transport == null) cfg.transport = "http";
        return cfg;
    }

    public boolean isLeader() { return "leader".equalsIgnoreCase(role); }
    public void setRole(String r) { this.role = r; }
}

