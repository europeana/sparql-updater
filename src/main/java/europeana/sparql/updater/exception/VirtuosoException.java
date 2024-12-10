package europeana.sparql.updater.exception;

/**
 * Thrown when there is an error communicating with Virtuoso
 */
public class VirtuosoException extends UpdaterException {

    public VirtuosoException(String msg, Throwable t) {
        super(msg, t);
    }

    public VirtuosoException(String msg) {
        super(msg);
    }

}
