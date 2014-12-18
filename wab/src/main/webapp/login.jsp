<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Context Server Login page</title>
</head>
<body>
<h1>Context Server login</h1>

<form action="j_security_check" method="POST">
    <label>Username</label><input type="text" name="j_username"/>
    <label>Password</label><input type="password" name="j_password"/>
    <input type="submit" name="submit" value="Submit"/>
</form>
</body>
</html>
