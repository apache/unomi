/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function () {
    // No session id is generated or sent from the browser. /login/authenticate calls Unomi with
    // trusted credentials, and a trusted caller is allowed to adopt the profile that owns the
    // session id it passes, so the id must come from server-side state only (the servlet derives
    // it from its own HttpSession). Sending a client-chosen id here would let anyone rebind
    // another visitor's profile.

    function show(ok, message) {
        var cls = ok ? "alert-success" : "alert-danger";
        $("#alert_placeholder").html(
            '<div class="alert ' + cls + '"><a class="close" data-dismiss="alert">×</a><span></span></div>'
        );
        $("#alert_placeholder .alert span").text(message);
    }

    $(function () {
        $("#loginForm").on("submit", function (event) {
            event.preventDefault();
            $.ajax({
                url: "/login/authenticate",
                type: "POST",
                data: {
                    firstName: $("#firstname").val(),
                    lastName: $("#lastname").val(),
                    email: $("#email").val(),
                    password: $("#password").val()
                },
                dataType: "json"
            }).done(function (body) {
                var email = body.profileProperties && body.profileProperties.email;
                show(true, "OK — profileId=" + body.profileId
                    + (email ? (", email=" + email) : "")
                    + ". Clear context-profile-id and login again with the same email to verify merge.");
            }).fail(function (xhr) {
                var body = xhr.responseJSON || {};
                var msg = body.error || body.errorMessage || xhr.responseText || ("HTTP " + xhr.status);
                show(false, msg);
            });
            return false;
        });
    });
})();
