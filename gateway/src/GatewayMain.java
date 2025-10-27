public class GatewayMain {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        long hbIntervalMs = 2000;
        int hbRetries = 2;

        // flags opcionais --port=8080 --hbIntervalMs=2000 --hbRetries=2
        for (String a : args) {
            if (a.startsWith("--port=")) port = Integer.parseInt(a.substring("--port=".length()));
            else if (a.startsWith("--hbIntervalMs=")) hbIntervalMs = Long.parseLong(a.substring("--hbIntervalMs=".length()));
            else if (a.startsWith("--hbRetries=")) hbRetries = Integer.parseInt(a.substring("--hbRetries=".length()));
        }

        Discovery discovery = new Discovery();
        GatewayHttpServer http = new GatewayHttpServer(discovery, port);
        http.start();

        Thread hb = new Thread(new HeartbeatTask(discovery, hbIntervalMs, hbRetries), "heartbeat");
        hb.setDaemon(true);
        hb.start();

        Thread.currentThread().join();
    }
}
