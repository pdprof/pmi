package workload.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Managing session lifecycle.
 * This servlet accepts only requests with GET method.
 */
@WebServlet(name = "SessionController", urlPatterns =
		{ SessionController.SESSION, SessionController.INVALIDATE })
public class SessionController extends HttpServlet {

	/** Location for session creation. */
	public static final String SESSION = "/session";

	/** Location for session invalidation. */
	public static final String INVALIDATE = "/invalidate";

	private static final long serialVersionUID = 1L;

	private final Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Creates new session or invalidates existing session.
	 * @param request Request Object
	 * @param response Response Object
	 * @throws IOException When fail to output results
	 * @throws ServletException When fail to manage session
	 */
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		String path = request.getServletPath();
		switch ((path == null) ? "" : path) {
		case SESSION:
			create(request, response);
			break;

		case INVALIDATE:
			invalidate(request, response);
			break;

		default:
			logger.severe(() -> "Unexpected location: ".concat(path));
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Creates new session.
	 * @param request Request Object
	 * @param response Response Object
	 * @throws IOException When fail to output results
	 * @throws ServletException When fail to create session
	 */
	private void create(HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		HttpSession session = request.getSession(false);
		if (session != null) {
			logger.severe(() -> "Session already exists: ".concat(session.getId()));
			response.setStatus(HttpServletResponse.SC_CONFLICT);
			return;
		}
		String id = request.getSession().getId();

		write(response,
				"<!DOCTYPE html>",
				"<html lang=\"ja\">",
				"<head>",
				"\t<meta charset=\"UTF-8\">",
				"\t<title>session created: " + id + "</title>",
				"</head>",
				"<body>",
				"\t<a href=\"invalidate\">invalidate session</a>",
				"</body>",
				"</html>");
	}

	/**
	 * Invalidates existing session.
	 * @param request Request Object
	 * @param response Response Object
	 * @throws IOException When fail to output results
	 * @throws ServletException When fail to invalidate session
	 */
	private void invalidate(HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		HttpSession session = request.getSession(false);
		if (session == null) {
			logger.severe("Session has been already invalidated.");
			response.setStatus(HttpServletResponse.SC_GONE);
			return;
		}
		String id = session.getId();
		session.invalidate();

		write(response,
				"<!DOCTYPE html>",
				"<html lang=\"ja\">",
				"<head>",
				"\t<meta charset=\"UTF-8\">",
				"\t<title>session invalidated: " + id + "</title>",
				"</head>",
				"<body>",
				"\t<a href=\"index.html\">return</a>",
				"</body>\n",
				"</html>");
	}

	private void write(HttpServletResponse response, String... lines)
			throws IOException {
		response.setContentType("text/html; charset=UTF-8");
		try (PrintWriter out = response.getWriter()) {
			for (String line : lines) {
				out.print(line);
				out.print("\r\n");
			}
		}
	}
}
