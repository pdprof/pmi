package collector.rest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;

/**
 * ExecutorService をラップして、非同期処理のコンテキストを提供するユーティリティーです。
 */
@ApplicationScoped
public class WorkerController {

	/**
	 * アプリケーション終了時に実行中のタスクに対する待機時間を指定するための環境変数名です。
	 * @see WorkorPool
	 */
	public static final String ENV_AWAIT_SECONDS = "SENDER_AWAIT_SECONDS";

	/** 管理下にあるタスクのIDとタスク自体とを関連付ける Map です。 */
	private final Map<String, Future<?>> tasks = new ConcurrentHashMap<>();

	private Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Register a task using ID and the Future instance to be controlled.
	 * @param id ID of the task to be registered
	 * @param task Future instance to execute
	 */
	public void attach(String id, Future<?> task) {
		tasks.put(id, task);
	}

	/**
	 * Cancels a task specified by ID.
	 * @param id ID of the task to be cancelled
	 * @param interrupt TODO
	 * @see #attach(Future, long)
	 */
	public void detach(String id, boolean interrupt) {
		Future<?> task = tasks.remove(id);
		if (interrupt && task != null && !task.isDone() && !task.isCancelled()) {
			task.cancel(true);
		}
	}

	@PreDestroy
	private void destroy() {
		for (Future<?> task : tasks.values()) {
			if (!task.isDone() && !task.isCancelled()) {
				task.cancel(true);
			}
		}

		for (Map.Entry<String, Future<?>> entry : tasks.entrySet()) {
			try {
				entry.getValue().get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				logger.log(Level.WARNING, e,
						() -> "Task " + entry.getKey() + " ended with some problem.");
			}
		}
	}

}
