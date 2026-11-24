package com.big.dreamer.doccentral.storage;

public class StorageBackupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StorageBackupException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageBackupException(String message) {
        super(message);
    }
}
