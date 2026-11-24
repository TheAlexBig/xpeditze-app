package com.big.dreamer.doccentral.license.service;

public class LicenseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LicenseException(String message) {
        super(message);
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
