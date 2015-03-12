<%--
  #%L
  context-server-wab
  $Id:$
  $HeadURL:$
  %%
  Copyright (C) 2014 - 2015 Jahia Solutions
  %%
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  
       http://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  #L%
  --%>
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
