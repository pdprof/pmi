package collector.rest.stat;

public class Failure {

	private byte[] throwable;

	private String error;

	public byte[] getThrowable() {
		return throwable;
	}

	public void setThrowable(byte[] throwable) {
		this.throwable = throwable;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

}
