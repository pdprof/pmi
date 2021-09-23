package collector.rest.stat;

/**
 * {@code
   {"value":"103350272","type":"java.lang.Long"}
 * }
 * @author aa307843
 *
 */
public class StatValue {

	private String value;

	private String type;

	public String getValue() {
		return value;
	}

	public String getType() {
		return type;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void setType(String type) {
		this.type = type;
	}

}
