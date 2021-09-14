package workload.api;

/**
 * 擬似的なワークロードの内容を表現ためのデータです。
 */
public class WorkloadInfo {

	private long duration = 15L * 1000L;

	private int multiplicity = 3;

	private long range = 3000L;

	/**
	 * 処理時間(ミリ秒単位)の期待値を取得します。
	 * @return 処理時間の期待値
	 */
	public long getDuration() {
		return duration;
	}

	/**
	 * 多重度を取得します。
	 * @return 多重度
	 */
	public int getMultiplicity() {
		return multiplicity;
	}

	/**
	 * 処理時間(ミリ秒単位)のばらつきを取得します。
	 * @return 処理時間のばらつき
	 */
	public long getRange() {
		return range;
	}

	/**
	 * 処理時間(ミリ秒単位)の期待値を設定します。デフォルト値は15000ミリ秒です。
	 * @param duration 処理時間の期待値
	 */
	public void setDuration(long duration) {
		this.duration = duration;
	}

	/**
	 * 多重度を設定します。デフォルト値は3です。
	 * @param multiplicity 多重度
	 */
	public void setMultiplicity(int multiplicity) {
		this.multiplicity = multiplicity;
	}

	/**
	 * 処理時間(ミリ秒単位)のばらつきを設定します。デフォルト値は3000ミリ秒です。
	 * @param range 処理時間のばらつき
	 */
	public void setRange(long range) {
		this.range = range;
	}

}
