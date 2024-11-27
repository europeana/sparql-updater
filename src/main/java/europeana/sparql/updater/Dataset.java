package europeana.sparql.updater;

import java.time.Instant;

/**
 * Represents the status of an Europeana dataset in the FTP server and in
 * Virtuoso
 *
 */
public class Dataset {

	public enum State {
		UP_TO_DATE, OUTDATED, CORRUPT, MISSING, TO_REMOVE
	}

	String id;
	Instant timestampFtp;
	Instant timestampSparql;
	State state;

	public Dataset(String id) {
		super();
		assert (id != null);
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Instant getTimestampFtp() {
		return timestampFtp;
	}

	public void setTimestampFtp(Instant instant) {
		this.timestampFtp = instant;
	}

	public Instant getTimestampSparql() {
		return timestampSparql;
	}

	public void setTimestampSparql(Instant timestampSparql) {
		this.timestampSparql = timestampSparql;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Dataset))
			return false;
		Dataset ds = (Dataset) obj;
		return id.equals(ds.getId());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public void checkState(Dataset dsAtSparql) {
		if (dsAtSparql == null)
			state = State.MISSING;
		else {
			setTimestampSparql(dsAtSparql.getTimestampSparql());
			if (timestampSparql == null || dsAtSparql.getState() == State.CORRUPT)
				state = State.CORRUPT;
			else if (timestampFtp.isAfter(timestampSparql))
				state = State.OUTDATED;
			else
				state = State.UP_TO_DATE;
		}
	}

	public boolean isCorruptAtSparql() {
		return timestampSparql == null;
	}

	public boolean isOutdatedAtSparql() {
		return false;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

}
