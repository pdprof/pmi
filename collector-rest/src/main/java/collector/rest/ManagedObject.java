package collector.rest;

/**
 * Object represents an element of REST response from MBeanServer.
 */
public class ManagedObject {

	private String objectName;

	private String className;

	private String url;

	public String getObjectName() {
		return objectName;
	}

	public String getClassName() {
		return className;
	}

	public String getURL() {
		return url;
	}

	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public void setURL(String uRL) {
		url = uRL;
	}

}
