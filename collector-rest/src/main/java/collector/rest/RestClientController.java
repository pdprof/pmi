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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
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

@WebServlet(name = "RestClientController", urlPatterns = "/requests/*",
		asyncSupported = true)
public class RestClientController extends HttpServlet {

	private static final long serialVersionUID = 1L;

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

	private static final List<String> EMPTY_PATHS = Arrays.asList(null, "", "/");

	private static final Pattern ID_PATTERN = Pattern.compile("^/([^/]+)(|/.*)$");

	private static final Comparator<StatisticsRequest> FIRST_COME =
			(left, right) -> (int) (left.getRequested() - right.getRequested());

	private static final GenericType<List<StatEntry>> STATISTICS =
			new GenericType<List<StatEntry>>() {
	};

	private final Logger logger = Logger.getLogger(getClass().getName());

	@Inject
	private WorkerController executor;

	private final Map<String, StatisticsRequest> requested = new ConcurrentHashMap<>();

	private Client client;

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

	protected void list(HttpServletResponse response) throws IOException {
		response.setContentType(MediaType.TEXT_HTML);
		response.setCharacterEncoding(UTF_8.name());

		List<StatisticsRequest> available = new ArrayList<>(requested.values());
		try (PrintWriter out = response.getWriter()) {
			out.print("<!DOCTYPE html>\r\n");
			out.print("<html lang=\"ja\">\r\n");
			out.print("<head>\r\n");
			out.print("\t<meta charset=\"UTF-8\">\r\n");
			out.print("\t<title>current requests</title>\r\n");
			out.print("</head>\r\n");
			out.print("<body>\r\n");
			available.stream()
					.sorted(FIRST_COME)
					.forEach(request -> render(out, request));
			out.print("</body>\r\n");
			out.print("</html>\r\n");
		}
	}

	protected void reserve(
			HttpServletRequest request, HttpServletResponse response) {
		StatisticsRequest task = new StatisticsRequest();
		task.setLocation(request.getParameter("location"));
		task.setQuery(request.getParameter("query"));
		task.setUser(request.getParameter("user"));
		task.setPassword(request.getParameter("password"));
		requested.put(task.getId(), task);

		refresh(request, response);
	}

	protected void finish(
			HttpServletRequest request, HttpServletResponse response, String id) {
		executor.detach(id, true);
		requested.remove(id);

		refresh(request, response);
	}

	protected void monitor(
			HttpServletRequest request, HttpServletResponse response, String id) {
		long start = System.currentTimeMillis();
		AtomicBoolean verified = new AtomicBoolean();
		long initial = ofNullable(request.getParameter("initial"))
				.map(Long::parseLong).orElse(15L) * 1000L;
		long period = ofNullable(request.getParameter("period"))
				.map(Long::parseLong).orElse(30L) * 1000L;

		AsyncContext context = createAsyncContext(request, response, id);
		RunnableFuture<?> task = new FutureTask<>(() -> {
			int remains = ofNullable(request.getParameter("times"))
					.map(Integer::parseInt)
					.filter(value -> value >= 0)
					.orElse(Integer.MAX_VALUE);
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

			Builder endpoint = createEndpoint(requested.get(id));

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
					logger.log(Level.WARNING, e,
							() -> "Cannot process: ".concat(e.toString()));
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

	@PostConstruct
	private void createClient() {
		try {
			TrustManager[] trustManagers = { DUMMY_TRUST_MANAGER };
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(null, trustManagers, null);
			client = ClientBuilder.newBuilder().sslContext(sslContext).build();
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			logger.log(Level.WARNING, e,
					() -> "Using default SSLContext: ".concat(e.toString()));
			client = ClientBuilder.newClient();
		}
	}

	private AsyncContext createAsyncContext(
			HttpServletRequest request, HttpServletResponse response, String id) {
		response.setContentType("text/csv");
		response.setCharacterEncoding(UTF_8.name());
		response.setHeader("Content-Disposition",
				"attachment; filename=\"" + id + ".csv\"");

		AsyncContext result = request.startAsync(request, response);
		result.setTimeout(-1);
		return result;
	}

	private Builder createEndpoint(StatisticsRequest work) {
		StringBuilder buffer = new StringBuilder();
		buffer.append(work.getUser()).append(':').append(work.getPassword());
		String encoded = Base64.getUrlEncoder().encodeToString(
				buffer.toString().getBytes(UTF_8));
		buffer.setLength(0);
		buffer.append("Basic ").append(encoded);

		return client.target(work.getLocation().concat(work.getQuery()))
				.request()
				.accept(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, buffer.toString());
	}

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

	private void render(PrintWriter out, StatisticsRequest request) {
		out.print("<ul style=\"list-style: none; border: 1px solid #ccccff; "
				+ "border-radius: 5px; background-color: #eeeeff;\">\r\n");

		out.print("<li>");
		out.print(request.getId());
		out.print("</li>\r\n");

		out.print("<li style=\"float: left;\"><form action=\"");
		out.print(request.getId());
		out.print("\" method=\"GET\" target=\"_blank\" "
				+ "onsubmit=\"elements.start.disabled = true;\">");
		out.print("<input name=\"initial\" size=\"4\" value=\"15\">");
		out.print("<input name=\"period\" size=\"4\" value=\"30\">");
		out.print("<input name=\"times\" size=\"4\" value=\"-1\">");
		out.print("<input type=\"submit\" name=\"start\" value=\"start\">");
		out.print("</form></li>\r\n");

		out.print("<li><form action=\"");
		out.print(request.getId());
		out.print("/finished\" method=\"POST\">");
		out.print("<input type=\"submit\" value=\"finish\">");
		out.print("</form></li>\r\n");

		out.print("<li style=\"clear: both;\">");
		out.print(request.getLocation());
		out.print(request.getQuery());
		out.print("</li>\r\n");

		out.print("</ul>\r\n");
	}

	private void refresh(
			HttpServletRequest request, HttpServletResponse response) {
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
		requested.remove(id);
	}

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
