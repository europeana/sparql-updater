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

    Instant startTime = Instant.now();
    Instant endTime;
    List<Dataset> updated = new ArrayList<>();
    List<Dataset> created = new ArrayList<>();
    List<Dataset> fixed = new ArrayList<>();
    List<Dataset> unchanged = new ArrayList<>();
    List<Dataset> removed = new ArrayList<>();
    Map<Dataset, String> failed = new HashMap<>();

    public UpdateReport() {
        // empty constructor, we set all fields using methods
    }

    public void wasUpdated(Dataset ds) {
        updated.add(ds);
    }

    public void wasCreated(Dataset ds) {
        created.add(ds);
    }

    public void wasFixed(Dataset ds) {
        fixed.add(ds);
    }

    public void failed(Dataset ds, String reason) {
        failed.put(ds, reason);
    }

    public void wasUnchanged(Dataset ds) {
        unchanged.add(ds);
    }

    public List<Dataset> getUpdated() {
        return updated;
    }

    public List<Dataset> getCreated() {
        return created;
    }

    public List<Dataset> getFixed() {
        return fixed;
    }

    public List<Dataset> getUnchanged() {
        return unchanged;
    }

    public Map<Dataset, String> getFailed() {
        return failed;
    }

    public void wasRemoved(Dataset ds) {
        removed.add(ds);
    }

    public String print() {
        StringBuilder sb = new StringBuilder();
        sb.append("Start time: ").append(startTime.toString()).append("\n");
        sb.append("End time: ").append(endTime.toString()).append("\n");
        Duration diff = Duration.between(startTime, endTime);
        sb.append(String.format("Duration: %d:%02d:%02d%n", diff.toHours(), diff.toMinutesPart(), diff.toSecondsPart()));

        if (!created.isEmpty()) {
            sb.append("Datasets created:\n");
            for (Dataset ds : created) {
                sb.append(" - ").append(ds.getId()).append("\n");
            }
        }
        if (!updated.isEmpty()) {
            sb.append("Datasets updated:\n");
            for (Dataset ds : updated) {
                sb.append(" - ").append(ds.getId()).append("\n");
            }
        }
        if (!fixed.isEmpty()) {
            sb.append("Datasets fixed:\n");
            for (Dataset ds : fixed) {
                sb.append(" - ").append(ds.getId()).append("\n");
            }
        }
        if (!removed.isEmpty()) {
            sb.append("Datasets removed:\n");
            for (Dataset ds : fixed) {
                sb.append(" - ").append(ds.getId()).append("\n");
            }
        }
        if (!failed.isEmpty()) {
            sb.append("Datasets failed:\n");
            for (Dataset ds : failed.keySet()) {
                sb.append(" - ").append(ds.getId()).append(": ").append(failed.get(ds)).append("\n");
            }
        }
        if (!unchanged.isEmpty()) {
            sb.append("Datasets unchanged:\n");
            for (Dataset ds : unchanged) {
                sb.append(" - ").append(ds.getId()).append("\n");
            }
        }
        return sb.toString();
    }

    public String printSummary(String sparqlNode) {
        return String.format("SPARQL node %s has been updated where %d datasets were updated and %d datasets were deleted",
                sparqlNode, updated.size()+created.size(), removed.size());
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
