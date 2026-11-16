package com.inventory.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtils {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private JsonUtils() {}

    public static String toJson(Object object) {
        if (object == null) return null;
        return GSON.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) {
        if (json == null || json.trim().isEmpty()) return null;
        return GSON.fromJson(json, classOfT);
    }

    public static Gson getGson() {
        return GSON;
    }
}
