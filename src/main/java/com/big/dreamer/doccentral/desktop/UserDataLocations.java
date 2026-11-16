package com.big.dreamer.doccentral.desktop;

import java.nio.file.Path;

public final class UserDataLocations {

    private static final String APPLICATION_DIRECTORY = "Xpeditze";
    private static final String LEGACY_APPLICATION_DIRECTORY = "Central Docs";

    private UserDataLocations() {
    }

    public static Path applicationDataDirectory() {
        String localApplicationData = System.getenv("LOCALAPPDATA");
        if (localApplicationData != null && !localApplicationData.isBlank()) {
            return preferCurrentOrLegacy(
                    Path.of(localApplicationData, APPLICATION_DIRECTORY),
                    Path.of(localApplicationData, LEGACY_APPLICATION_DIRECTORY));
        }
        return preferCurrentOrLegacy(
                Path.of(System.getProperty("user.home"), ".xpeditze"),
                Path.of(System.getProperty("user.home"), ".central-docs"));
    }

    public static Path documentsDirectory() {
        return preferCurrentOrLegacy(
                Path.of(System.getProperty("user.home"), "Documents", APPLICATION_DIRECTORY, "Documents"),
                Path.of(System.getProperty("user.home"), "Documents", LEGACY_APPLICATION_DIRECTORY, "Documents"));
    }

    private static Path preferCurrentOrLegacy(Path current, Path legacy) {
        if (current.toFile().exists() || !legacy.toFile().exists()) {
            return current;
        }
        return legacy;
    }
}
