package collector.rest;

import java.util.UUID;

public class StatisticsRequest {

	private String location;

	private String query;

	private String user;

	private String password;

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

	public long getRequested() {
		return requested;
	}

}
