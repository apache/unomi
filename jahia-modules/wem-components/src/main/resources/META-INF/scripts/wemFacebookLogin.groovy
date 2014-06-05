println("<script type=\"text/javascript\">\n");

println("    // This is called with the results from from FB.getLoginStatus().\n" +
        "    function statusChangeCallback(response) {\n" +
        "        console.log('statusChangeCallback');\n" +
        "        console.log(response);\n" +
        "        // The response object is returned with a status field that lets the\n" +
        "        // app know the current login status of the person.\n" +
        "        // Full docs on the response object can be found in the documentation\n" +
        "        // for FB.getLoginStatus().\n" +
        "        if (response.status === 'connected') {\n" +
        "            // Logged into your app and Facebook.\n" +
        "            testAPI();\n" +
        "        } else if (response.status === 'not_authorized') {\n" +
        "            // The person is logged into Facebook, but not your app.\n" +
        "            document.getElementById('status').innerHTML = 'Please log ' +\n" +
        "                    'into this app.';\n" +
        "        } else {\n" +
        "            // The person is not logged into Facebook, so we're not sure if\n" +
        "            // they are logged into this app or not.\n" +
        "            document.getElementById('status').innerHTML = 'Please log ' +\n" +
        "                    'into Facebook.';\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // This function is called when someone finishes with the Login\n" +
        "    // Button.  See the onlogin handler attached to it in the sample\n" +
        "    // code below.\n" +
        "    function checkLoginState() {\n" +
        "        FB.getLoginStatus(function (response) {\n" +
        "            statusChangeCallback(response);\n" +
        "        });\n" +
        "    }\n" +
        "\n" +
        "    window.fbAsyncInit = function () {\n" +
        "        FB.init({\n" +
        "            appId: '${resource.node.resolveSite.properties['facebookAppId'].string}',\n" +
        "            cookie: true,  // enable cookies to allow the server to access\n" +
        "            // the session\n" +
        "            xfbml: true,  // parse social plugins on this page\n" +
        "            version: 'v2.0' // use version 2.0\n" +
        "        });\n" +
        "\n" +
        "        // Now that we've initialized the JavaScript SDK, we call\n" +
        "        // FB.getLoginStatus().  This function gets the state of the\n" +
        "        // person visiting this page and can return one of three states to\n" +
        "        // the callback you provide.  They can be:\n" +
        "        //\n" +
        "        // 1. Logged into your app ('connected')\n" +
        "        // 2. Logged into Facebook, but not your app ('not_authorized')\n" +
        "        // 3. Not logged into Facebook and can't tell if they are logged into\n" +
        "        //    your app or not.\n" +
        "        //\n" +
        "        // These three cases are handled in the callback function.\n" +
        "\n" +
        "        FB.getLoginStatus(function (response) {\n" +
        "            statusChangeCallback(response);\n" +
        "        });\n" +
        "\n" +
        "    };\n" +
        "\n" +
        "    // Load the SDK asynchronously\n" +
        "    (function (document, elementToCreate, id) {\n" +
        "        var js, fjs = document.getElementsByTagName(elementToCreate)[0];\n" +
        "        if (document.getElementById(id)) return;\n" +
        "        js = document.createElement(elementToCreate);\n" +
        "        js.id = id;\n" +
        "        js.src = \"//connect.facebook.net/en_US/sdk.js\";\n" +
        "        js.type = \"text/javascript\";\n" +
        "        fjs.parentNode.insertBefore(js, fjs);\n" +
        "    }(document, 'script', 'facebook-jssdk'));\n" +
        "\n" +
        "    // Here we run a very simple test of the Graph API after login is\n" +
        "    // successful.  See statusChangeCallback() for when this call is made.\n" +
        "    function testAPI() {\n" +
        "        console.log('Welcome!  Fetching your information.... ');\n" +
        "        FB.api('/me', function (response) {\n" +
        "            console.log('Good to see you, ' + response.name + '.');\n" +
        "            document.getElementById('status').innerHTML = 'Good to see you, ' +\n" +
        "                    response.name;\n" +
        "\n" +
        "            // update digitalData W3 Customer Experience Digital Data structure\n" +
        "            digitalData.user[0].profiles[0].profileInfo.userName = response.name;\n" +
        "            digitalData.user[0].profiles[0].profileInfo.email = response.email;\n" +
        "            digitalData.user[0].profiles[0].profileInfo.gender = response.gender;\n" +
        "            digitalData.user[0].profiles[0].profileInfo.firstName = response.first_name;\n" +
        "            digitalData.user[0].profiles[0].profileInfo.lastName = response.last_name;\n" +
        "\n" +
        "            wemi.saveContext(\"${wemiContextServerURL}/context.js\", digitalData, function (xhr) {\n" +
        "                console.log(\"User context updated successfully.\")\n" +
        "            });\n" +
        "        });\n" +
        "    }")

println("</script>");
