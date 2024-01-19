package tools.tracesviewer;

import java.util.Vector;

public class TracesSessions extends Vector<TracesSession> {
	private static final long serialVersionUID = 1L;

	protected String name = null;

    public TracesSessions() {
        super();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
