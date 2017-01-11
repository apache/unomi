<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

Configuration
=============

Changing the default configuration
----------------------------------

If you want to change the default configuration, you can perform any modification you want in the $MY_KARAF_HOME/etc directory.

The context server configuration is kept in the $MY_KARAF_HOME/etc/org.apache.unomi.web.cfg . It defines the
addresses and port where it can be found :

    contextserver.address=localhost
    contextserver.port=8181
    contextserver.secureAddress=localhost
    contextserver.securePort=9443
    contextserver.domain=apache.org

If you need to specify an Elasticsearch cluster name that is different than the default, it is recommended to do this
BEFORE you start the server for the first time, or you will loose all the data you have stored previously.

To change the cluster name, first create a file called 

    $MY_KARAF_HOME/etc/org.apache.unomi.persistence.elasticsearch.cfg

with the following contents:

    cluster.name=contextElasticSearch
    index.name=context
    
Secured events configuration
---------------------------

If you need to secure some events, that will be sent only by a trusted third party server, you can update the file :

    $MY_KARAF_HOME/etc/org.apache.unomi.thirdparty.cfg

Ususally, login events, which operate on profiles and do merge on protected properties, must be secured. For each
trusted third party server, you need to add these 3 lines :

thirdparty.provider1.key=secret-key
thirdparty.provider1.ipAddresses=127.0.0.1,::1
thirdparty.provider1.allowedEvents=login,download

The events set in allowedEvents will be secured and will only be accepted if the call comes from the specified IP
address, and if the secret-key is passed in the X-Unomi-Peer header.    

Installing the MaxMind GeoIPLite2 IP lookup database
----------------------------------------------------

The Context Server requires an IP database in order to resolve IP addresses to user location.
The GeoLite2 database can be downloaded from MaxMind here :
http://dev.maxmind.com/geoip/geoip2/geolite2/

Simply download the GeoLite2-City.mmdb file into the "etc" directory.

Installing Geonames database
----------------------------

Context server includes a geocoding service based on the geonames database ( http://www.geonames.org/ ). It can be
used to create conditions on countries or cities.

In order to use it, you need to install the Geonames database into . Get the "allCountries.zip" database from here :
http://download.geonames.org/export/dump/

Download it and put it in the "etc" directory, without unzipping it.
Edit $MY_KARAF_HOME/etc/org.apache.unomi.geonames.cfg and set request.geonamesDatabase.forceImport to true, import should start right away.
Otherwise, import should start at the next startup. Import runs in background, but can take about 15 minutes.
At the end, you should have about 4 million entries in the geonames index.
 
REST API Security
-----------------

The Context Server REST API is protected using JAAS authentication and using Basic or Digest HTTP auth.
By default, the login/password for the REST API full administrative access is "karaf/karaf".

The generated package is also configured with a default SSL certificate. You can change it by following these steps :

1. Replace the existing keystore in $MY_KARAF_HOME/etc/keystore by your own certificate :
 
    http://wiki.eclipse.org/Jetty/Howto/Configure_SSL
    
2. Update the keystore and certificate password in $MY_KARAF_HOME/etc/custom.properties file :
 
```
    org.osgi.service.http.secure.enabled = true
    org.ops4j.pax.web.ssl.keystore=${karaf.etc}/keystore
    org.ops4j.pax.web.ssl.password=changeme
    org.ops4j.pax.web.ssl.keypassword=changeme
    org.osgi.service.http.port.secure=9443
```

You should now have SSL setup on Karaf with your certificate, and you can test it by trying to access it on port 9443.

3. Changing the default Karaf password can be done by modifying the etc/users.properties file

4. You will also need to change the user/password information in the org.apache.unomi.cluster.cfg file : 

```
    cluster.group=default
    cluster.jmxUsername=karaf
    cluster.jmxPassword=karaf
    cluster.jmxPort=1099
```

Automatic profile merging
-------------------------

The context server is capable of merging profiles based on a common property value. In order to use this, you must
add the MergeProfileOnPropertyAction to a rule (such as a login rule for example), and configure it with the name
 of the property that will be used to identify the profiles to be merged. An example could be the "email" property,
 meaning that if two (or more) profiles are found to have the same value for the "email" property they will be merged
 by this action.
 
Upon merge, the old profiles are marked with a "mergedWith" property that will be used on next profile access to delete
the original profile and replace it with the merged profile (aka "master" profile). Once this is done, all cookie tracking
will use the merged profile.

To test, simply configure the action in the "login" or "facebookLogin" rules and set it up on the "email" property. 
Upon sending one of the events, all matching profiles will be merged.

Securing a production environment
---------------------------------

Before going live with a project, you should *absolutely* read the following section that will help you setup a proper 
secure environment for running your context server.         

Step 1: Install and configure a firewall 

You should setup a firewall around your cluster of context servers and/or Elasticsearch nodes. If you have an 
application-level firewall you should only allow the following connections open to the whole world : 

 - http://localhost:8181/context.js
 - http://localhost:8181/eventcollector

All other ports should not be accessible to the world.

For your Context Server client applications (such as the Jahia CMS), you will need to make the following ports 
accessible : 

    8181 (Context Server HTTP port) 
    9443 (Context Server HTTPS port)
    
The context server actually requires HTTP Basic Auth for access to the Context Server administration REST API, so it is
highly recommended that you design your client applications to use the HTTPS port for accessing the REST API.

The user accounts to access the REST API are actually routed through Karaf's JAAS support, which you may find the
documentation for here : 

 - http://karaf.apache.org/manual/latest/users-guide/security.html
    
The default username/password is 

    karaf/karaf
    
You should really change this default username/password as soon as possible. To do so, simply modify the following
file : 

    $MY_KARAF_HOME/etc/users.properties

For your context servers, and for any standalone Elasticsearch nodes you will need to open the following ports for proper
node-to-node communication : 9200 (Elasticsearch REST API), 9300 (Elasticsearch TCP transport)

Of course any ports listed here are the default ports configured in each server, you may adjust them if needed.

Step 2 : Follow industry recommended best practices for securing Elasticsearch

You may find more valuable recommendations here : 

- https://www.elastic.co/blog/found-elasticsearch-security
- https://www.elastic.co/blog/scripting-security
    
Step 4 : Setup a proxy in front of the context server

As an alternative to an application-level firewall, you could also route all traffic to the context server through
a proxy, and use it to filter any communication.

Integrating with an Apache HTTP web server
------------------------------------------

If you want to setup an Apache HTTP web server in from of Apache Unomi, here is an example configuration using 
mod_proxy.

In your Unomi package directory, in /etc/org.apache.unomi.web.cfg for unomi.apache.org
   
   contextserver.address=unomi.apache.org
   contextserver.port=80
   contextserver.secureAddress=unomi.apache.org
   contextserver.securePort=443
   contextserver.domain=apache.org

Main virtual host config:

    <VirtualHost *:80>
            Include /var/www/vhosts/unomi.apache.org/conf/common.conf
    </VirtualHost>
    
    <IfModule mod_ssl.c>
        <VirtualHost *:443>
            Include /var/www/vhosts/unomi.apache.org/conf/common.conf
    
            SSLEngine on
    
            SSLCertificateFile    /var/www/vhosts/unomi.apache.org/conf/ssl/24d5b9691e96eafa.crt
            SSLCertificateKeyFile /var/www/vhosts/unomi.apache.org/conf/ssl/apache.org.key
            SSLCertificateChainFile /var/www/vhosts/unomi.apache.org/conf/ssl/gd_bundle-g2-g1.crt
    
    
            <FilesMatch "\.(cgi|shtml|phtml|php)$">
                    SSLOptions +StdEnvVars
            </FilesMatch>
            <Directory /usr/lib/cgi-bin>
                    SSLOptions +StdEnvVars
            </Directory>
            BrowserMatch "MSIE [2-6]" \
                    nokeepalive ssl-unclean-shutdown \
                    downgrade-1.0 force-response-1.0
            BrowserMatch "MSIE [17-9]" ssl-unclean-shutdown
    
        </VirtualHost>
    </IfModule>
    
common.conf:

    ServerName unomi.apache.org
    ServerAdmin webmaster@apache.org
    
    DocumentRoot /var/www/vhosts/unomi.apache.org/html
    CustomLog /var/log/apache2/access-unomi.apache.org.log combined
    <Directory />
            Options FollowSymLinks
            AllowOverride None
    </Directory>
    <Directory /var/www/vhosts/unomi.apache.org/html>
            Options FollowSymLinks MultiViews
            AllowOverride None
            Order allow,deny
            allow from all
    </Directory>
    <Location /cxs>
        Order deny,allow
        deny from all
        allow from 88.198.26.2
        allow from www.apache.org
    </Location>
    
    RewriteEngine On
    RewriteCond %{REQUEST_METHOD} ^(TRACE|TRACK)
    RewriteRule .* - [F]
    ProxyPreserveHost On
    ProxyPass /server-status !
    ProxyPass /robots.txt !
    
    RewriteCond %{HTTP_USER_AGENT} Googlebot [OR]
    RewriteCond %{HTTP_USER_AGENT} msnbot [OR]
    RewriteCond %{HTTP_USER_AGENT} Slurp
    RewriteRule ^.* - [F,L]
    
    ProxyPass / http://localhost:8181/ connectiontimeout=20 timeout=300 ttl=120
    ProxyPassReverse / http://localhost:8181/
