package com.misionesmod.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ClientHudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    public enum CornerPosition {
        TOP_RIGHT("Superior Derecha"),
        TOP_LEFT("Superior Izquierda"),
        BOTTOM_RIGHT("Inferior Derecha"),
        BOTTOM_LEFT("Inferior Izquierda");

        public final String displayName;

        CornerPosition(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum WidgetScale {
        COMPACT("Pequeño", 0.8f),
        NORMAL("Normal", 1.0f),
        LARGE("Grande", 1.2f);

        public final String displayName;
        public final float scaleFactor;

        WidgetScale(String displayName, float scaleFactor) {
            this.displayName = displayName;
            this.scaleFactor = scaleFactor;
        }
    }

    public static boolean widgetEnabled = true;
    public static CornerPosition cornerPosition = CornerPosition.TOP_RIGHT;
    public static WidgetScale widgetScale = WidgetScale.NORMAL;

    public static void load() {
        try {
            File configDir = FabricLoader.getInstance().getConfigDir().toFile();
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            configFile = new File(configDir, "misionesmod_hud.json");
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    ConfigData data = GSON.fromJson(reader, ConfigData.class);
                    if (data != null) {
                        widgetEnabled = data.widgetEnabled;
                        if (data.cornerPosition != null) {
                            try {
                                cornerPosition = CornerPosition.valueOf(data.cornerPosition);
                            } catch (Exception ignored) {}
                        }
                        if (data.widgetScale != null) {
                            try {
                                widgetScale = WidgetScale.valueOf(data.widgetScale);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } else {
                save();
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        if (configFile == null) {
            File configDir = FabricLoader.getInstance().getConfigDir().toFile();
            configFile = new File(configDir, "misionesmod_hud.json");
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            ConfigData data = new ConfigData();
            data.widgetEnabled = widgetEnabled;
            data.cornerPosition = cornerPosition.name();
            data.widgetScale = widgetScale.name();
            GSON.toJson(data, writer);
        } catch (Exception ignored) {}
    }

    private static class ConfigData {
        boolean widgetEnabled = true;
        String cornerPosition = "TOP_RIGHT";
        String widgetScale = "NORMAL";
    }
}
