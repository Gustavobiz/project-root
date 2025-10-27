public class LogEntry {
    public final int index;
    public final int term;
    public final String command;

    public LogEntry(int index, int term, String command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }
}
