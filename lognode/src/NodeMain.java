public class NodeMain {
    public static void main(String[] args) throws Exception {
        NodeConfig cfg = NodeConfig.fromArgs(args);
        LogCore core = new LogCore();

        // Inicia HTTP do no
        NodeHttpServer http = new NodeHttpServer(cfg, core);
        http.start();
        // Inicia servidores UDP e TCP (sempre; simples) — ou condicione por cfg.transport
new Thread(new UdpServer(cfg, core), "udp-"+cfg.port).start();
new Thread(new TcpServer(cfg, core), "tcp-"+cfg.port).start();

// Auto-registro no Gateway
try {
    int tries = 0;
    while (tries < 5) {
        try {
            String json = String.format(
                "{\"nodeId\":\"%s\",\"ip\":\"%s\",\"port\":%d,\"role\":\"%s\",\"transport\":\"%s\"}",
                cfg.nodeId,            // use o nodeId passado
                cfg.ip,                // 👈 usa o IP da linha de comando (NÃO fixo "localhost")
                cfg.port,
                cfg.role,
                cfg.transport
            );
            String resp = NodeNet.postJson(cfg.gateway + "/register", json, 5000); // 👈 5s
            System.out.println("Register response: " + resp);
            break; // sucesso
        } catch (Exception ex) {
            tries++;
            System.err.println("Register failed (" + tries + "): " + ex.getMessage());
            try { Thread.sleep(1000L * tries); } catch (InterruptedException ignore) {}
        }
    }
} catch (Exception e) {
    System.err.println("Register fatal: " + e.getMessage());
}

        Thread.currentThread().join();
    }
}
