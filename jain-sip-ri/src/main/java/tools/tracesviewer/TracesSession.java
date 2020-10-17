package tools.tracesviewer;

import java.util.*;
import java.io.*;

public class TracesSession extends Vector<TracesMessage> implements Serializable {
	private static final long serialVersionUID = 1L;

	protected String logDescription = null;
    protected String name = null;
    protected String info = null;

    public TracesSession() {
        super();
    }

    public TracesSession(String name, String info) {
        this();
        this.name = name;
        this.info = info;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getInfo() {
        return info;
    }

    public void setLogDescription(String logDescription) {
        this.logDescription = logDescription;
    }

    public String getLogDescription() {
        return logDescription;
    }
    public TracesSession(MessageLogList messageLogList) {
        super();
        this.logDescription = messageLogList.description;
        Iterator<TracesMessage> it = messageLogList.iterator();
        while (it.hasNext()) {
            TracesMessage tracesMessage = it.next();
            super.add(tracesMessage);
        }
    }
}
