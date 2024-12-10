package europeana.sparql.updater.virtuoso;

/**
 * Represents the result from executing a command in the VirtuosoGraphManagerCl
 */
public final class CommandResult {
    String successMessage;
    String errorMessage;

    private CommandResult(String successMessage, String errorMessage) {
        super();
        this.successMessage = successMessage;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return successMessage != null;
    }

    /**
     * Create a new command result with an error message
     * @param message the error message
     * @return new CommandResult object
     */
    public static CommandResult error(String message) {
        return new CommandResult(null, message);
    }

    /**
     * Create a new command result with an exitcode and process output
     * @param exitCode the exit code
     * @param output the process output
     * @return new CommandResult object
     */
    public static CommandResult error(int exitCode, String output) {
        return new CommandResult(null, "Exit code: " + exitCode + " ; output:\n" + output);
    }

    /**
     * Create a new command result with a success message
     * @param message the success message
     * @return new CommandResult object
     */
    public static CommandResult success(String message) {
        return new CommandResult(message, null);
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
