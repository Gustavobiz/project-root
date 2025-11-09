import java.net.*;
import java.nio.charset.StandardCharsets;

public class UdpServer implements Runnable {
    private final NodeConfig cfg;
    private final LogCore core;

    public UdpServer(NodeConfig cfg, LogCore core){ this.cfg=cfg; this.core=core; }

    @Override public void run() {
        try (DatagramSocket ds = new DatagramSocket(cfg.port)) {
            byte[] buf = new byte[4096];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ds.receive(p);
                String body = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);

                int index = extractInt(body,"index");
                int term  = extractInt(body,"term");
                String cmd = extractString(body,"command");
                int leaderCommit = extractInt(body,"leaderCommit");

                boolean ok = false;
                synchronized (core) {
                    if (index>0 && cmd!=null) {
                        core.append(new LogEntry(index, term, cmd));
                        if (leaderCommit>0) core.commitTo(leaderCommit);
                        ok = true;
                    }
                }
                String resp = ok? String.format("{\"success\":true,\"matchIndex\":%d}", index)
                                : "{\"success\":false}";
                byte[] out = resp.getBytes(StandardCharsets.UTF_8);
                DatagramPacket ack = new DatagramPacket(out, out.length, p.getAddress(), p.getPort());
                ds.send(ack);
            }
        } catch (Exception ignore) {}
    }

    private static int extractInt(String json, String field){
        String t="\""+field+"\""; int i=json.indexOf(t); if(i<0)return 0;
        int c=json.indexOf(':',i+t.length()); if(c<0)return 0;
        int e=json.indexOf(',',c+1); if(e<0) e=json.indexOf('}',c+1);
        try { return Integer.parseInt(json.substring(c+1,e).replace("\"","").trim()); }
        catch(Exception ex){ return 0; }
    }
    private static String extractString(String json, String field){
        String t="\""+field+"\""; int i=json.indexOf(t); if(i<0)return null;
        int c=json.indexOf(':',i+t.length()); if(c<0)return null;
        int f=json.indexOf('"',c+1), s=json.indexOf('"',f+1);
        if(f<0||s<0)return null; return json.substring(f+1,s);
    }
}
