<!DOCTYPE html><%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<html lang="ja"><% if (request.getSession(false) != null) request.getSession().invalidate(); %>
<head>
	<meta charset="UTF-8">
	<title>invalidate session</title>
</head>
<body>
	<a href="index.html">return</a>
</body>
</html>
