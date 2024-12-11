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
    List<Dataset> updated = new ArrayList<>();
    List<Dataset> created = new ArrayList<>();
    List<Dataset> fixed = new ArrayList<>();
    List<Dataset> unchanged = new ArrayList<>();
    List<Dataset> removed = new ArrayList<>();
    Map<Dataset, String> failed = new HashMap<>();
    Exception error;

    public UpdateReport(String nodeId) {
        this.nodeId = nodeId;
    }

    public UpdateReport(String nodeId, Exception error) {
        this.nodeId = nodeId;
        this.error = error;
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

    public String printSummary() {
        StringBuilder s = new StringBuilder();
        s.append("Update of SPARQL node ").append(nodeId);

        if (endTime == null) {
            s.append(" was aborted.\n");
        } else {
            Duration diff = Duration.between(startTime, endTime);
            s.append(" completed in ").append(diff.toHours()).append("h")
                    .append(diff.toMinutesPart()).append("m")
                    .append(diff.toSecondsPart()).append("s.\n");
        }

        if (error != null) {
            s.append("It failed with error \"")
                    .append(error.getMessage() == null ? error.toString() : error.getMessage()).append("\".\n");
        }
        s.append(created.size()).append(" new datasets were added, ")
                .append(updated.size()).append(" datasets were updated, and ")
                .append(removed.size()).append(" were deleted.");
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
