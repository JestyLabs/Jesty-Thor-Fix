package com.thor.displaypowertest;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Map;

/**
 * Private, capped support history. Disk work is queued by SharedPreferences
 * apply() only when an event was emitted; normal 1 Hz samples cause no writes.
 * This journal never affects the updater's safety decisions or daemon state.
 */
final class EventHistoryJournal {
    private static final String STORAGE = "local_event_history";
    private static final String KEY = "history_v1";

    private final SharedPreferences preferences;
    private final EventHistoryModel model;

    EventHistoryJournal(Context context) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        model = EventHistoryModel.decode(preferences.getString(KEY, null));
    }

    void onSample(Map<String, String> values) {
        if (model.sample(values, System.currentTimeMillis())) persist();
    }

    void onUnavailable() {
        if (model.unavailable(System.currentTimeMillis())) persist();
    }

    List<EventHistoryModel.Entry> entries() { return model.entries(); }

    void clear() {
        model.clear();
        preferences.edit().remove(KEY).apply();
    }

    private void persist() {
        preferences.edit().putString(KEY, model.encode()).apply();
    }
}
