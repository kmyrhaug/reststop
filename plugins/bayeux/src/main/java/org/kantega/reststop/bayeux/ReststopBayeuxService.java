/*
 * Copyright 2016 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kantega.reststop.bayeux;

import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;

import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 *
 */
public class ReststopBayeuxService implements BayeuxService, BayeuxService.MessageListener {

    private ReststopBayeuxServiceManager bayeuxServiceManager;
    private String channelName;

    private List<MessageListener> listeners;

    public ReststopBayeuxService(ReststopBayeuxServiceManager bayeuxServiceManager, String channelName) {
        this.bayeuxServiceManager = bayeuxServiceManager;
        this.channelName = channelName;
        this.listeners = new LinkedList<>();
    }


    public BayeuxServiceManager getBayeuxServiceManager() {
        return bayeuxServiceManager;
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public synchronized void addListener(BayeuxService.MessageListener listener) {
        listener = requireNonNull(listener, "May not be null: listener");
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public synchronized void removeListener(BayeuxService.MessageListener listener) {
        listeners.remove(listener);
    }

    @Override
    public List<MessageListener> getListeners() {
        return new LinkedList<>(listeners);
    }

    @Override
    public void close() {
        listeners.clear();
        this.bayeuxServiceManager.destroyService(channelName);
        channelName = null;
    }

    @Override
    public void accept(BayeuxService bayeuxService, ServerSession serverSession, ServerMessage serverMessage) {
        for (MessageListener listener : listeners) {
            listener.accept(this, serverSession, serverMessage);
        }
    }
}