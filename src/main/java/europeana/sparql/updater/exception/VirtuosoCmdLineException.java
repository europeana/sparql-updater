package europeana.sparql.updater.exception;

/**
 * Thrown when there is an error communicating with Virtuoso
 */
public class VirtuosoCmdLineException extends UpdaterException {

    public VirtuosoCmdLineException(String msg, Throwable t) {
        super(msg, t);
    }

    public VirtuosoCmdLineException(String msg) {
        super(msg);
    }

}
