package collector.rest.stat;

/**
 * {@code
   {"name":"Heap","value":{"value":"103350272","type":"java.lang.Long"}}
 * }
 */
public class StatEntry {

	private String name;

	private StatValue value;

	public String getName() {
		return name;
	}

	public StatValue getValue() {
		return value;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setValue(StatValue value) {
		this.value = value;
	}

}
