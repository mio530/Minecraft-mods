package de.mio.ryzeteams.storage;

/** Wraps low-level persistence failures so callers need not handle SQLException. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
