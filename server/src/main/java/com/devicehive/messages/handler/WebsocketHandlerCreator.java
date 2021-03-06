package com.devicehive.messages.handler;

import com.devicehive.websockets.util.AsyncMessageSupplier;
import com.devicehive.websockets.util.WebsocketSession;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.concurrent.locks.Lock;

public class WebsocketHandlerCreator implements HandlerCreator {

    private static final Logger logger = LoggerFactory.getLogger(WebsocketHandlerCreator.class);

    private final Session session;
    private final AsyncMessageSupplier deliverer;
    private final Lock lock;
    private final Runnable postHandler;

    public WebsocketHandlerCreator(Session session, String lockAttribute,
                                   AsyncMessageSupplier deliverer, Runnable postHandler) {
        this.session = session;
        this.deliverer = deliverer;
        this.lock = (Lock) session.getUserProperties().get(lockAttribute);
        this.postHandler = postHandler;
    }

    public WebsocketHandlerCreator(Session session, String lockAttribute, AsyncMessageSupplier deliverer) {
        this(session, lockAttribute, deliverer, null);
    }

    @Override
    public Runnable getHandler(final JsonElement message) {
        logger.debug("Websocket subscription notified");
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!session.isOpen()) {
                        return;
                    }
                    try {
                        lock.lock();
                        logger.debug("Add messages to queue process for session " + session.getId());
                        WebsocketSession.addMessagesToQueue(session, message);
                    } finally {
                        lock.unlock();
                        deliverer.deliverMessages(session);
                    }
                } finally {
                    if (postHandler != null) {
                        postHandler.run();
                    }
                }
            }
        };
    }

}
