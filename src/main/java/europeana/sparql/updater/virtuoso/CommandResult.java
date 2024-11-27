package europeana.sparql.updater.virtuoso;

/**
 * Represents the result from executing a command in the VirtuosoGraphManagerCl
 */
public class CommandResult {
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

	public static CommandResult error(String message) {
		return new CommandResult(null, message);
	}

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
