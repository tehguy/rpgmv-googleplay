/*
   Copyright 2019 tehguy

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.pixima.libmvgoogleplay;

import android.app.Activity;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;

import com.google.android.gms.games.EventsClient;
import com.google.android.gms.games.event.Event;
import com.google.android.gms.games.event.EventBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class EventsHandler extends AbstractHandler<EventsClient> {
    static final String INTERFACE_NAME = "__google_play_events";

    private Map<String, EventShell> mEventsCache;
    private Map<String, Long> mOfflineEventCache;

    EventsHandler(Activity activity) {
        super(activity);

        mEventsCache = new HashMap<>();
        mOfflineEventCache = new HashMap<>();
    }

    @SuppressWarnings("WeakerAccess")
    @JavascriptInterface
    public void incrementEvent(@NonNull String eventId, @NonNull Long stepAmount) {
        if (mClient != null) {
            mClient.increment(eventId, stepAmount.intValue());
        }

        if (!mEventsCache.isEmpty()) {
            EventShell shell = mEventsCache.get(eventId);

            if (shell == null) return;

            shell.val += stepAmount;
            mEventsCache.put(eventId, shell);

            Long offlineStep = mOfflineEventCache.get(eventId);

            if (offlineStep != null) {
                mOfflineEventCache.put(eventId, offlineStep + stepAmount);
            }
            else {
                mOfflineEventCache.put(eventId, stepAmount);
            }
        }
    }

    @JavascriptInterface
    public String getAllEventDataAsJson() {
        return !mEventsCache.isEmpty() ?
                gson.toJson(mEventsCache.values().toArray())
                : null;
    }

    @JavascriptInterface
    public String getEventDataAsJson(String eventId) {
        return !mEventsCache.isEmpty() ?
                gson.toJson(mEventsCache.get(eventId))
                : null;
    }

    void cacheEvents(boolean forceReload) {
        mClient.load(forceReload)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) return;

                    try {
                        EventBuffer eventBuffer = Objects.requireNonNull(task.getResult()).get();

                        int buffSize = eventBuffer != null ? eventBuffer.getCount() : 0;

                        for (int i = 0; i < buffSize; i++) {
                            Event event = eventBuffer.get(i).freeze();
                            EventShell shell = new EventShell(event);

                            mEventsCache.put(shell.id, shell);
                        }

                        if (eventBuffer != null) {
                            eventBuffer.release();
                        }
                    }
                    catch (NullPointerException ignored) {}
                });
    }

    void incrementCachedEvents() {
        if (mOfflineEventCache.isEmpty()) return;

        for (Map.Entry<String, Long> entry : mOfflineEventCache.entrySet()) {
            incrementEvent(entry.getKey(), entry.getValue());
        }

        mOfflineEventCache.clear();
    }

    private static class EventShell {
        String id;
        String name;
        String desc;
        String formattedVal;
        Uri imageUri;
        long val;

        EventShell(@NonNull Event event) {
            id = event.getEventId();
            name = event.getName();
            desc = event.getDescription();
            formattedVal = event.getFormattedValue();
            imageUri = event.getIconImageUri();
            val = event.getValue();
        }
    }
}
