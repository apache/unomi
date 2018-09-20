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


Apache Unomi Web Tracker
=================================

This is the web tracker for apache-unomi ( http://unomi.apache.org/ )

It's included in unomi server, and can be served directly by unomi : https://github.com/apache/incubator-unomi/tree/master/extensions/web-tracker

This package can be used in a JS app to interact with unomi.

## Getting started

Adds tracker to your app :

`npm add unomi-analytics`

Then 

```javascript
unomiTracker.initialize({
     'Apache Unomi': {
         scope: 'my-app',
         url: 'http://unomi:8181'
     }
 });
 
unomiTracker.ready(function() {
    console.log("Unomi context loaded - profile id : "+window.cxs.profileId + ", sessionId="+window.cxs.sessionId);
});
```

Then send events :
```javascript
unomiTracker.page()
```
