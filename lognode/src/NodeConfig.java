public class NodeConfig {
    public String role = "follower";           // leader | follower
    public String transport = "http";          // http | udp | grpc (começamos por http)
    public int port = 5000;                    // porta do nó
    public String gateway = "http://localhost:8080";
    public String nodeId = "nodeA";

    public static NodeConfig fromArgs(String[] args) {
        NodeConfig c = new NodeConfig();
        for (String a : args) {
            if (a.startsWith("--role="))      c.role = a.substring("--role=".length());
            else if (a.startsWith("--port=")) c.port = Integer.parseInt(a.substring("--port=".length()));
            else if (a.startsWith("--gateway=")) c.gateway = a.substring("--gateway=".length());
            else if (a.startsWith("--transport=")) c.transport = a.substring("--transport=".length());
            else if (a.startsWith("--nodeId=")) c.nodeId = a.substring("--nodeId=".length());
        }
        return c;
    }
public void setRole(String r){ this.role = r; }


    public boolean isLeader() { return "leader".equalsIgnoreCase(role); }
}
