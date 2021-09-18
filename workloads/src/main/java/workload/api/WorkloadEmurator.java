package workload.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * 擬似的なワークロードを発生させるためのエンドポイントを提供するリソースクラスです。
 */
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/v1")
public class WorkloadEmurator {

	private Logger logger = Logger.getLogger(getClass().getName());

	@Resource(lookup = "jdbc/derby")
	private DataSource resource;

	@Inject
	private WorkorPool executor;

	private Client client = ClientBuilder.newClient();

	@POST
	@Path("/jdbc")
	public Map<String, Object> jdbc(WorkloadInfo workload) {
		Map<String, Object> result = new LinkedHashMap<>();

		for (int i = 0; i < workload.getMultiplicity(); i++) {
			executor.getAsyncContext().submit(() -> {
				Thread current = Thread.currentThread();
				logger.info(() -> "start ".concat(current.getName()));
				try (Connection connection = resource.getConnection()) {
					Thread.sleep(getWait(workload));
					logger.info(() -> "done ".concat(current.getName()));
				} catch (InterruptedException e) {
					current.interrupt();
					logger.info(() -> "done ".concat(current.getName()));
				} catch (SQLException e) {
					logger.log(Level.SEVERE, e,
							() -> "abort ".concat(current.getName()));
				}
			});
		}

		result.put("result", Boolean.TRUE);
		result.put("multiplicity", workload.getMultiplicity());
		return result;
	}

	@POST
	@Path("/session")
	public Map<String, Object> session(
			@Context HttpServletRequest request, WorkloadInfo workload) {
		Map<String, Object> result = new LinkedHashMap<>();

		String root = "http://localhost:9080".concat(request.getContextPath());
		for (int i = 0; i < workload.getMultiplicity(); i++) {
			executor.getAsyncContext().submit(() -> {
				Thread current = Thread.currentThread();
				logger.info(() -> "start ".concat(current.getName()));

				WebTarget creator = client.target(root.concat("/session"));
				Cookie session;
				try (Response created = creator.request().get()) {
					session = created.getCookies().get("JSESSIONID");
					Thread.sleep(getWait(workload));
				} catch (InterruptedException e) {
					current.interrupt();
					logger.info(() -> "abort ".concat(current.getName()));
					return;
				} catch (ProcessingException e) {
					logger.log(Level.SEVERE, e,
							() -> "abort ".concat(current.getName()));
					return;
				}

				WebTarget terminator = client.target(root.concat("/invalidate"));
				try (Response invalidated = terminator.request().cookie(session).get()) {
					logger.info(() -> "done ".concat(current.getName()));
				} catch (ProcessingException e) {
					logger.log(Level.SEVERE, e,
							() -> "abort ".concat(current.getName()));
				}
			});
		}

		result.put("result", Boolean.TRUE);
		result.put("multiplicity", workload.getMultiplicity());
		return result;
	}

	private long getWait(WorkloadInfo workload) {
		long base = workload.getDuration() - workload.getRange();
		long offset = (long) (Math.random() * (workload.getRange() * 2L));
		return base + offset;
	}

	@PreDestroy
	private void close() {
		client.close();
	}

}
