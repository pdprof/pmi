package collector.rest;

import java.util.UUID;

public class StatisticsRequest {

	public static final int INVALID = -1;

	public static final int STARTABLE = 0;

	public static final int STARTED = 1;

	private String location;

	private String query;

	private String user;

	private String password;

	private int status = INVALID;

	private final long requested = System.currentTimeMillis();

	private final String id = UUID.randomUUID().toString();

	public String getId() {
		return id;
	}

	public String getLocation() {
		return location;
	}

	public String getQuery() {
		return query;
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public long getRequested() {
		return requested;
	}

	public int getStatus() {
		return status;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setStatus(int status) {
		switch (status) {
		case INVALID:
		case STARTABLE:
		case STARTED:
			this.status = status;
			break;

		default:
			throw new IllegalArgumentException("invalid value: " + status);
		}
	}

}
