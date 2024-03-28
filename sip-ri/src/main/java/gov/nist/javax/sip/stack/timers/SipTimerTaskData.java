/*
 * Mobius Software LTD
 * Copyright 2019 - 2024, Mobius Software LTD and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package gov.nist.javax.sip.stack.timers;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Separate the timer task data from the timer task itself.
 */
public abstract class SipTimerTaskData implements Externalizable {
    Object timerGenericData;

    public Object getTimerGenericData() {
        return timerGenericData;
    }

    public void setTimerGenericData(Object timerGenericData) {
        this.timerGenericData = timerGenericData;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        timerGenericData = in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(timerGenericData);        
    }

}
