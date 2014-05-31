println("<script type=\"text/javascript\">\n" +
                    "\n" +
                    "  var _gaq = _gaq || [];\n");

println(" _gaq.push(['_setAccount', '${wemiContextServerURL}']);\n" +
                    "  _gaq.push(['_trackPageview', '${resourceUrl}']);\n")
gaMap.each {
  entry-> println("  _gaq.push(['_trackPageview', '${entry.value}']);\n")
}
println("  (function() {\n" +
                    "    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;\n" +
                    "    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';\n" +
                    "    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);\n" +
                    "  })();\n" +
                    "\n" +
                    "</script>")
