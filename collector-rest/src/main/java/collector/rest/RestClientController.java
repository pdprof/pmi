package collector.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import collector.rest.stat.StatEntry;

/**
 * Controller communicating with RESTConnector for a MBeanServer.
 */
@WebServlet(name = "RestClientController", urlPatterns = "/requests/*", asyncSupported = true)
public class RestClientController extends HttpServlet {

	private static final long serialVersionUID = 1L;

	/** A dummy implementation for skipping server certification check. */
	private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	};

	/** A dummy implementation for skipping host identity check of TLS connection. */
	private static final HostnameVerifier DUMMY_HOSTNAME_VERIFIER =
			(host, session) -> host != null;

	/** A Comparator ordering StatisticsRequest(s) on a first-come, first-served basis. */
	private static final Comparator<StatisticsRequest> FIRST_COME =
			(left, right) -> (int) (left.getRequested() - right.getRequested());

	private static final GenericType<List<ManagedObject>> BEANS =
			new GenericType<List<ManagedObject>>() {
	};

	private static final GenericType<List<StatEntry>> STATISTICS =
			new GenericType<List<StatEntry>>() {
	};

	private static final List<String> EMPTY_PATHS = Arrays.asList(null, "", "/");

	private static final Pattern ID_PATTERN = Pattern.compile("^/([^/]+)(|/.*)$");

	private static final Pattern NAME_PATTERN =
			Pattern.compile("^WebSphere:.*type=(\\w+Stats|perf)(|,.*)$");

	private final Logger logger = Logger.getLogger(getClass().getName());

	@Inject
	private WorkerController executor;

	private final Map<String, StatisticsRequest> reserved = new ConcurrentHashMap<>();

	private final Client client;

	public RestClientController() {
		super();
		client = createClient();
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (EMPTY_PATHS.contains(request.getPathInfo())) {
			reserve(request, response);
		} else {
			Matcher matcher = ID_PATTERN.matcher(request.getPathInfo());
			if (matcher.matches() && "/finished".equals(matcher.group(2))) {
				finish(request, response, matcher.group(1));
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (EMPTY_PATHS.contains(request.getPathInfo())) {
			list(response);
		} else {
			Matcher matcher = ID_PATTERN.matcher(request.getPathInfo());
			if (matcher.matches() && matcher.group(2).isEmpty()) {
				monitor(request, response, matcher.group(1));
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}

	/**
	 * Render the list of statistics to {@code response}.
	 * @param response response object
	 * @throws IOException fail to render the list of statistics
	 */
	protected void list(HttpServletResponse response) throws IOException {
		response.setContentType(MediaType.TEXT_HTML);
		response.setCharacterEncoding(UTF_8.name());

		List<StatisticsRequest> available = new ArrayList<>(reserved.values());
		try (PrintWriter out = response.getWriter()) {
			out.print("<!DOCTYPE html>\r\n");
			out.print("<html lang=\"ja\">\r\n");
			out.print("<head>\r\n");
			out.print("\t<meta charset=\"UTF-8\">\r\n");
			out.print("\t<title>current requests</title>\r\n");
			out.print("<link href=\"/collector-rest/dashboard.css\" rel=\"stylesheet\">");
			out.print("</head>\r\n");
			out.print("<body>\r\n");
			available.stream()
					.sorted(FIRST_COME)
					.forEach(request -> render(out, request));
			out.print("</body>\r\n");
			out.print("</html>\r\n");
		}
	}

	/**
	 * Reserve a statistics and record its ID.
	 * @param request request object
	 * @param response response object
	 */
	protected void reserve(HttpServletRequest request, HttpServletResponse response) {
		StatisticsRequest work = new StatisticsRequest();
		work.setLocation(request.getParameter("location"));
		work.setQuery("/");
		work.setUser(request.getParameter("user"));
		work.setPassword(request.getParameter("password"));

		try (Response obtained = createInvocation(work).get()) {
			String query = request.getParameter("query");
			if (query.isEmpty()) {
				reserve(work, obtained.readEntity(BEANS));
			} else {
				work.setQuery(query);
				work.setStatus(StatisticsRequest.STARTABLE);
				reserved.put(work.getId(), work);
			}
		} catch (ProcessingException e) {
			logger.log(Level.SEVERE, e,
					() -> work.getId().concat(": ").concat(e.getLocalizedMessage()));
			work.setLocation(e.getLocalizedMessage());
			work.setQuery("");
			reserved.put(work.getId(), work);
		}

		refresh(request, response);
	}

	/**
	 * Reserve all statistics listed by {@code MBeanServer}.
	 * @param base {@code StatisticsRequest} represents basic information
	 * @param beans list of metadata for {@code MBean}
	 */
	private void reserve(StatisticsRequest base, List<ManagedObject> beans) {
		for (ManagedObject target : beans) {
			String query = target.getObjectName();
			if (NAME_PATTERN.matcher(query).matches()) {
				StatisticsRequest work = new StatisticsRequest();
				work.setLocation(base.getLocation());
				work.setQuery(query.concat("/attributes"));
				work.setUser(base.getUser());
				work.setPassword(base.getPassword());
				work.setStatus(StatisticsRequest.STARTABLE);

				reserved.put(work.getId(), work);
			}
		}
	}

	/**
	 * Start to gathering the statistics.
	 * @param request request object
	 * @param response response object
	 * @param id ID of statistics to monitor
	 */
	protected void monitor(HttpServletRequest request, HttpServletResponse response, String id) {
		reserved.get(id).setStatus(StatisticsRequest.STARTED);

		long start = System.currentTimeMillis();
		AtomicBoolean verified = new AtomicBoolean();
		long initial = ofNullable(request.getParameter("initial"))
				.map(Long::parseLong).orElse(15L) * 1000L;
		long period = ofNullable(request.getParameter("period"))
				.map(Long::parseLong).orElse(30L) * 1000L;

		AsyncContext context = createAsyncContext(request, response, id);
		RunnableFuture<?> task = new FutureTask<>(() -> {
			int remains = ofNullable(request.getParameter("times"))
					.map(Integer::parseInt).filter(value -> value >= 0).orElse(Integer.MAX_VALUE);
			int attempts = remains;
			logger.info(() -> String.valueOf(attempts).concat(" attempts start"));

			PrintWriter out;
			try {
				out = response.getWriter();
			} catch (IOException e) {
				logger.log(Level.SEVERE, e,
						() -> "Cannot initialize response: ".concat(e.toString()));
				complete(context, id);
				return;
			}

			Builder endpoint = createInvocation(reserved.get(id));

			long next = start + initial;
			if (!untill(context, next, id)) {
				return;
			}

			while (remains > 0) {
				int current = remains;
				logger.info(() -> String.valueOf(current).concat(" remains"));

				try (Response obtained = endpoint.get()) {
					String data = format(next, obtained, verified);
					logger.info(() -> id.concat(": ").concat(data));
					out.print(data);
					out.flush();
				} catch (RuntimeException e) {
					logger.log(Level.WARNING, e, () -> "Cannot process: ".concat(e.toString()));
				}

				next += period;
				if ((--remains > 0) && !untill(context, next, id)) {
					return;
				}
			}

			complete(context, id);
		}, id);
		context.start(task);
		executor.attach(id, task);
		logger.info(() -> "Task is scheduled for ".concat(id));
	}

	/**
	 * Finish the statistics specified by {@code id}.
	 * @param request request object
	 * @param response response object
	 * @param id ID of statistics to end
	 */
	protected void finish(HttpServletRequest request, HttpServletResponse response, String id) {
		executor.detach(id, true);
		reserved.remove(id);

		refresh(request, response);
	}

	/**
	 * Create JAX-RS client with dummy {@code TrustManager} and dummy {@code HostnameVerifier}.
	 * @return JAX-RS client
	 */
	private Client createClient() {
		try {
			TrustManager[] trustManagers = { DUMMY_TRUST_MANAGER };
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(null, trustManagers, null);
			return ClientBuilder.newBuilder()
					.sslContext(sslContext)
					.hostnameVerifier(DUMMY_HOSTNAME_VERIFIER)
					.build();
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			logger.log(Level.WARNING, e,
					() -> "Using default SSLContext: ".concat(e.toString()));
			return ClientBuilder.newClient();
		}
	}

	/**
	 * Create context for Servlet asynchronous operation.
	 * @param request request object
	 * @param response response object
	 * @param id ID of statistics to monitor
	 * @return {@code AsyncContext} represents the context for asynchronous operation
	 */
	private AsyncContext createAsyncContext(
			HttpServletRequest request, HttpServletResponse response, String id) {
		response.setContentType("text/csv");
		response.setCharacterEncoding(UTF_8.name());
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + id + ".csv\"");

		AsyncContext result = request.startAsync(request, response);
		result.setTimeout(-1);
		return result;
	}

	/**
	 * Create a {@code Builder} instance for REST API invocation.
	 * @param work request for a statistics to be invoked
	 * @return {@code Builder} represents REST API invocation
	 */
	private Builder createInvocation(StatisticsRequest work) {
		StringBuilder buffer = new StringBuilder();
		buffer.append(work.getUser()).append(':').append(work.getPassword());
		String encoded = Base64.getUrlEncoder().encodeToString(buffer.toString().getBytes(UTF_8));
		buffer.setLength(0);
		buffer.append("Basic ").append(encoded);
		String authorization = buffer.toString();

		return client.target(work.getLocation().concat(work.getQuery()).replace(' ', '+'))
				.request()
				.accept(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, authorization);
	}

	/**
	 * Format a statistic returned by RESTConnector.
	 * @param timestamp current time in milliseconds
	 * @param obtained response including entity returned by RESTConnector
	 * @param first flag determining the header line has not been rendered
	 * @return a set of formated statistic
	 */
	private String format(long timestamp, Response obtained, AtomicBoolean first) {
		if (!obtained.hasEntity()) {
			return timestamp + ",(no contents)\r\n";
		}

		List<StatEntry> values;
		try {
			values = obtained.readEntity(STATISTICS);

			StringBuilder lines = new StringBuilder(1024);
			if (!first.getAndSet(true)) {
				String separator = "Time,";
				for (StatEntry entry : values) {
					lines.append(separator).append(entry.getName());
					separator = ",";
				}
				lines.append("\r\n");
			}

			lines.append(timestamp);
			for (StatEntry entry : values) {
				lines.append(',').append(entry.getValue().getValue());
			}
			lines.append("\r\n");

			return lines.toString();
		} catch (ProcessingException e) {
			return timestamp + ",(not started)\r\n";
		}
	}

	/**
	 * Render a request for statistics.
	 * @param out {@code PrintWriter} to which the request is rendered
	 * @param work request for a statistics to be rendered
	 */
	private void render(PrintWriter out, StatisticsRequest work) {
		if (work.getStatus() >= StatisticsRequest.STARTABLE) {
			out.print("<ul class=\"active\">\r\n");
		} else {
			out.print("<ul class=\"inactive\">\r\n");
		}

		out.print("<li>");
		out.print(work.getId());
		out.print("</li>\r\n");

		out.print("<li><form method=\"GET\" action=\"");
		out.print(work.getId());
		out.print("\" target=\"");
		out.print(work.getId());
		out.print("\" onsubmit=\"elements.start.disabled = true;\"");
		if (work.getStatus() == StatisticsRequest.STARTABLE) {
			out.print(" class=\"active\">");
		} else {
			out.print(" class=\"inactive\">");
		}
		out.print("initial(sec.)-period(sec.)-times ");
		out.print("<input name=\"initial\" size=\"4\" value=\"15\">-");
		out.print("<input name=\"period\" size=\"4\" value=\"30\">-");
		out.print("<input name=\"times\" size=\"4\" value=\"-1\"> ");
		out.print("<input type=\"submit\" name=\"start\" value=\"start\"> ");
		out.print("</form>");

		out.print("<form method=\"POST\" action=\"");
		out.print(work.getId());
		out.print("/finished\">");
		out.print("&nbsp;<input type=\"submit\" value=\"finish\">");
		out.print("</form></li>\r\n");

		out.print("<li>");
		out.print(work.getLocation());
		out.print(work.getQuery());
		out.print("</li>\r\n");

		out.print("</ul>\r\n");
	}

	/**
	 * Redirect response to refresh the list of request for a statistics.
	 * @param request request object
	 * @param response response object
	 */
	private void refresh(HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_SEE_OTHER);
		StringBuilder location = new StringBuilder()
				.append(request.getContextPath())
				.append(request.getServletPath())
				.append('/');
		response.setHeader(HttpHeaders.LOCATION, location.toString());
	}

	private void complete(AsyncContext context, String id) {
		context.complete();
		logger.info(() -> id.concat(" finished."));
		executor.detach(id, false);
		reserved.remove(id);
	}

	/**
	 * Wait till the specified time, aborting as soon as possible if the task is interrupted.
	 * @param context {@code AsyncContext} associated with current task
	 * @param time the time when end to wait
	 * @param id ID of current task
	 * @return {@code true} if timed-up without interruption
	 */
	private boolean untill(AsyncContext context, long time, String id) {
		while (System.currentTimeMillis() < time) {
			if (Thread.interrupted()) {
				Thread.currentThread().interrupt();
				complete(context, id);
				return false;
			}
			Thread.yield();
		}
		return true;
	}

	@PreDestroy
	private void close() {
		client.close();
	}

}
