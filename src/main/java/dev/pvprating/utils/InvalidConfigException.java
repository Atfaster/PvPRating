package dev.pvprating.utils;

public class InvalidConfigException extends Exception {
    public InvalidConfigException(String string) {
        super(string);
    }

    public static void CheckInvalidConfigs() throws InvalidConfigException {
    }
}
