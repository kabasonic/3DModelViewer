package org.kabasonic.util.event;

import java.util.EventObject;

public interface EventListener {
    boolean onEvent(EventObject event);
}
