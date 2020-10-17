package tools.tracesviewer;

import java.util.*;
import java.io.*;

public class TracesSessions extends Vector<TracesSession> implements Serializable {
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
