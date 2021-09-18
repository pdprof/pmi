package workload.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

/**
 * 非同期処理を実行するためのスレッド・プールを提供します。
 * <p>指定可能な環境変数</p>
 * <dl>
 * <dt>{@value #ENV_AWAIT_SECONDS}</dt>
 * <dd>アプリケーション終了時に実行中のタスクに対する待機時間(秒)(デフォルト値: 15)</dd>
 * </dl>
 */
@ApplicationScoped
public class WorkorPool {

	/**
	 * アプリケーション終了時に実行中のタスクに対する待機時間を指定するための環境変数名です。
	 * @see WorkorPool
	 */
	public static final String ENV_AWAIT_SECONDS = "SENDER_AWAIT_SECONDS";

	/**
	 * スレッド・プールか管理するワーカー・スレッドの数を指定するための環境変数名です。
	 * @see WorkorPool
	 */
	public static final String ENV_POOL_SIZE = "SENDER_POOL_SIZE";

	private Logger logger = Logger.getLogger(WorkorPool.class.getName());

	/** アプリケーション終了時に実行中のタスクに対する待機時間(秒)です。 */
	private long awaiting = 15;

	@Resource(lookup = "java:comp/DefaultManagedThreadFactory")
	private ManagedThreadFactory factory;

	/** スレッド・プールを管理する ExecutorService です。 */
	private ExecutorService executor;

	/**
	 * スレッド・プールにタスクを投入するための ExecutorService を取得します。
	 * @return ExecutorService のインスタンス
	 */
	@Produces
	public ExecutorService getAsyncContext() {
		return executor;
	}

	/**
	 * 環境変数の値を取得します。環境変数に設定が無い場合は、デフォルト値を使用します。
	 * @param name 環境変数名
	 * @param defaultValue 環境変数が指定されていない場合のデフォルト値
	 * @return 環境変数の値
	 */
	private String getEnvValue(String name, String defaultValue) {
		String value = System.getenv(name);
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		return value;
	}

	@PostConstruct
	private void initialize() {
		try {
			awaiting = Long.parseLong(getEnvValue(ENV_AWAIT_SECONDS, "15"));
		} catch (NumberFormatException e) {
			logger.log(Level.SEVERE, e,
					() -> ENV_AWAIT_SECONDS + "の値が不正なため設定を変更できません。");
		}

		executor = Executors.newCachedThreadPool(factory);
	}

	@PreDestroy
	private void destroy() {
		executor.shutdown();
		try {
			executor.awaitTermination(awaiting, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		executor.shutdownNow();
	}

}
