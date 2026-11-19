package de.mio.ryzeteams.model;

/**
 * Thrown by domain/service operations when an action is invalid or not
 * permitted. The message is a short, human-readable English sentence that the
 * command layer shows directly to the player.
 */
public class TeamException extends RuntimeException {

    public TeamException(String message) {
        super(message);
    }
}
