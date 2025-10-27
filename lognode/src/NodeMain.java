public class NodeMain {
    public static void main(String[] args) throws Exception {
        NodeConfig cfg = NodeConfig.fromArgs(args);
        LogCore core = new LogCore();

        // Inicia HTTP do nó
        NodeHttpServer http = new NodeHttpServer(cfg, core);
        http.start();
// Auto-registro no Gateway
try {
    String json = String.format(
      "{\"nodeId\":\"%s\",\"ip\":\"localhost\",\"port\":%d,\"role\":\"%s\",\"transport\":\"%s\"}",
      cfg.nodeId, cfg.port, cfg.role, cfg.transport
    );
    String resp = NodeNet.postJson(cfg.gateway + "/register", json, 1500);
    System.out.println("Register response: " + resp);
} catch (Exception e) {
    System.err.println("Register failed: " + e.getMessage());
}

        Thread.currentThread().join();
    }
}
