
println("<script type=\"text/javascript\">\n");

println("    // Load the WEMI context script asynchronously\n" +
        "    (function (document, elementToCreate, id) {\n" +
        "        var js, fjs = document.getElementsByTagName(elementToCreate)[0];\n" +
        "        if (document.getElementById(id)) return;\n" +
        "        js = document.createElement(elementToCreate);\n" +
        "        js.id = id;\n" +
        "        js.src = \"/${wemiContextServerURL}\";\n" +
        "        js.type = \"text/javascript\";\n" +
        "        fjs.parentNode.insertBefore(js, fjs);\n" +
        "    }(document, 'script', 'wemi-context'));")

println("</script>");
