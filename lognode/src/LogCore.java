import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LogCore {
    private final List<LogEntry> log = new ArrayList<>();
    private final AtomicInteger commitIndex = new AtomicInteger(0);
    private final AtomicInteger term = new AtomicInteger(1);

    // Estado do no (chave/valor) aplicado somente apos commit
    private final Map<String,String> kv = new HashMap<>();

    public synchronized int append(LogEntry e){
        log.add(e);
        return e.index;
    }

    public synchronized LogEntry readByIndex(int idx){
        return log.stream().filter(le -> le.index == idx).findFirst().orElse(null);
    }

    public int getCommitIndex(){ return commitIndex.get(); }

    // Avança commitIndex e aplica entradas pendentes no estado KV
    public synchronized void commitTo(int idx){
        int prev = commitIndex.get();
        int next = Math.max(prev, idx);
        for (int i = prev + 1; i <= next; i++) {
            LogEntry e = readByIndex(i);
            if (e != null) apply(e);
        }
        commitIndex.set(next);
    }

    // Aplica um comando simples: "PUT k=v"
    private void apply(LogEntry e){
        String cmd = e.command;
        if (cmd == null) return;
        cmd = cmd.trim();
        if (cmd.toUpperCase().startsWith("PUT")) {
            // formato: PUT k=v
            String[] parts = cmd.split("\\s+", 2);
            if (parts.length == 2) {
                String[] kvp = parts[1].split("=", 2);
                if (kvp.length == 2) {
                    kv.put(kvp[0].trim(), kvp[1].trim());
                }
            }
        }
        // (poderia adicionar DELETE k, etc.)
    }

    // Leitura do estado aplicado
    public synchronized String get(String key){
        return kv.get(key);
    }

    public int nextIndex(){ return log.size() + 1; }
    public int getTerm(){ return term.get(); }
    public String getRoleString(boolean isLeader){ return isLeader ? "leader" : "follower"; }
}
