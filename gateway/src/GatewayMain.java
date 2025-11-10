public class GatewayMain {
    public static void main(String[] args) throws Exception {
        int httpPort = 8080;
        int tcpPort  = 8081;
        int udpPort  = 8082;
        long hbIntervalMs = 2000;
        int hbRetries = 2;

        
        // --httpPort=8080 --tcpPort=8081 --udpPort=8082 --hbIntervalMs=2000 --hbRetries=2
        for (String a : args) {
            if (a.startsWith("--httpPort=")) httpPort = Integer.parseInt(a.substring("--httpPort=".length()));
            else if (a.startsWith("--tcpPort=")) tcpPort = Integer.parseInt(a.substring("--tcpPort=".length()));
            else if (a.startsWith("--udpPort=")) udpPort = Integer.parseInt(a.substring("--udpPort=".length()));
            else if (a.startsWith("--hbIntervalMs=")) hbIntervalMs = Long.parseLong(a.substring("--hbIntervalMs=".length()));
            else if (a.startsWith("--hbRetries=")) hbRetries = Integer.parseInt(a.substring("--hbRetries=".length()));
        }

        Discovery discovery = new Discovery();

        // HTTP REST 
        GatewayHttpServer http = new GatewayHttpServer(discovery, httpPort);
        http.start();

        // Proxies TCP/UDP que encaminham para POST /log/append do próprio gateway
        new Thread(new GatewayTcpServer(httpPort), "gateway-tcp-" + tcpPort) .start();
        new Thread(new GatewayUdpServer(httpPort), "gateway-udp-" + udpPort) .start();

        // Heartbeat + eleição automática
        Thread hb = new Thread(new HeartbeatTask(discovery, hbIntervalMs, hbRetries), "heartbeat");
        hb.setDaemon(true);
        hb.start();

        System.out.println("Gateway ON: http="+httpPort+" tcp="+tcpPort+" udp="+udpPort);
        Thread.currentThread().join();
    }
}
