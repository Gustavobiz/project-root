import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TcpServer implements Runnable {
    private final NodeConfig cfg;
    private final LogCore core;

    public TcpServer(NodeConfig cfg, LogCore core){ this.cfg=cfg; this.core=core; }

    @Override public void run() {
        try (ServerSocket ss = new ServerSocket(cfg.port)) {
            while (true) {
                try (Socket s = ss.accept()) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                    String body = br.readLine(); // JSON por linha

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
                    bw.write(resp); bw.write("\n"); bw.flush();
                } catch (Exception ignore) {}
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
