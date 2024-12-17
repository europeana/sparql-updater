package europeana.sparql.updater;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A record of what happened during a run of the updater. Records what happened
 * for each dataset and error messages in case of failure.
 *
 */
public class UpdateReport {

    String nodeId;
    Instant startTime = Instant.now();
    Instant endTime;
    List<Dataset> created = new ArrayList<>();
    List<Dataset> updated = new ArrayList<>();
    List<Dataset> fixed = new ArrayList<>();
    List<Dataset> removed = new ArrayList<>();
    List<Dataset> unchanged = new ArrayList<>();
    Map<Dataset, String> failed = new HashMap<>();
    Exception updateStartError;

    /**
     * Initialize a new (successful) update report
     * @param serverId identifier of the pod/server that was updated
     */
    public UpdateReport(String serverId) {
        this.nodeId = serverId;
    }

    /**
     * Initialize a new update report where the update failed
     * @param serverId identifier of the pod/server that was updated
     * @param error the reason why the update failed
     */
    public UpdateReport(String serverId, Exception error) {
        this.nodeId = serverId;
        this.updateStartError = error;
    }

    /**
     * Add a dataset to the list of newly created datasets
     * @param ds dataset that was created
     */
    public void addCreated(Dataset ds) {
        created.add(ds);
    }

    /**
     * Add a dataset to the list of updated datasets
     * @param ds dataset that was updated
     */
    public void addUpdated(Dataset ds) {
        updated.add(ds);
    }

    /**
     * Add a dataset to the list of fixed corrupt datasets
     * @param ds dataset that was fixed
     */
    public void addFixed(Dataset ds) {
        fixed.add(ds);
    }

    /**
     * Add a dataset to the list of datasets that were removed
     * @param ds dataset that was removed
     */
    public void addRemoved(Dataset ds) {
        removed.add(ds);
    }

    /**
     * Add a dataset to the list of datasets that were not changed
     * @param ds dataset that did not change
     */
    public void addUnchanged(Dataset ds) {
        unchanged.add(ds);
    }

    /**
     * Add a dataset to the list of datasets failed to process
     * @param ds dataset that was not processed properly
     * @param reason string describing why the dataset failed (error message)
     */
    public void addFailed(Dataset ds, String reason) {
        failed.put(ds, reason);
    }

    public List<Dataset> getCreated() {
        return created;
    }

    public List<Dataset> getUpdated() {
        return updated;
    }

    public List<Dataset> getFixed() {
        return fixed;
    }

    public List<Dataset> getRemoved() {
        return removed;
    }

    public List<Dataset> getUnchanged() {
        return unchanged;
    }

    public Map<Dataset, String> getFailed() {
        return failed;
    }

    /**
     * Generate a short text describing the update
     * @return string describing the update process
     */
    public String printSummary() {
        StringBuilder s = new StringBuilder();
        s.append("Update of SPARQL node ").append(nodeId);

        if (endTime == null) {
            s.append(" was aborted.\n");
        } else {
            Duration diff = Duration.between(startTime, endTime);
            s.append(" completed in ").append(diff.toHours()).append("h")
                    .append(diff.toMinutesPart()).append("m")
                    .append(diff.toSecondsPart()).append("s with the following actions:\n");
        }

        if (updateStartError != null) {
            s.append("It failed with error \"")
                    .append(updateStartError.getMessage() == null ? updateStartError.toString() : updateStartError.getMessage()).append("\".\n");
        }
        s.append("created: ").append(created.size())
                .append(", updated: ").append(updated.size())
                .append(", fixed: ").append(fixed.size())
                .append(", deleted: ").append(removed.size())
                .append(", failed: ").append(failed.size());
        if (failed.size() > 0) {
            s.append("\nThe following datasets failed:\n");
            int counter=0;
            for (Map.Entry<Dataset, String> entry : failed.entrySet()) {
            	if( counter > 10 ) { // list at most 10 of the failed datasets
            		s.append("  ...\n");  
            		break;
            	}
                s.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                counter++;
            }
        }
        return s.toString();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

}
