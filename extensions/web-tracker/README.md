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

This extension is providing the web tracker to start collecting visitors data on your website. 
The tracker is implemented as an integration of [analytics.js](https://github.com/segmentio/analytics.js) for Unomi.

## Getting started

Extension can be tested at : `http://localhost:8181/tracker/index.html` 

In your page include unomiOptions and include code snippet from `snippet.min.js` :

```html
<script type="text/javascript">
        var unomiOption = {
            scope: 'realEstateManager',
            url: 'http://localhost:8181'
        };
        window.unomiTracker||(window.unomiTracker={}),function(){function e(e){for(unomiTracker.initialize({"Apache Unomi":unomiOption});n.length>0;){var r=n.shift(),t=r.shift();unomiTracker[t]&&unomiTracker[t].apply(unomiTracker,r)}}for(var n=[],r=["trackSubmit","trackClick","trackLink","trackForm","initialize","pageview","identify","reset","group","track","ready","alias","debug","page","once","off","on","personalize"],t=0;t<r.length;t++){var i=r[t];window.unomiTracker[i]=function(e){return function(){var r=Array.prototype.slice.call(arguments);return r.unshift(e),n.push(r),window.unomiTracker}}(i)}unomiTracker.load=function(){var n=document.createElement("script");n.type="text/javascript",n.async=!0,n.src=unomiOption.url+"/tracker/unomi-tracker.min.js",n.addEventListener?n.addEventListener("load",function(n){"function"==typeof e&&e(n)},!1):n.onreadystatechange=function(){"complete"!==this.readyState&&"loaded"!==this.readyState||e(window.event)};var r=document.getElementsByTagName("script")[0];r.parentNode.insertBefore(n,r)},document.addEventListener("DOMContentLoaded",unomiTracker.load),unomiTracker.page()}();
</script>
```

`window.unomiTracker` can be used to send additional events when needed.

Check analytics.js API [here](https://segment.com/docs/sources/website/analytics.js/). All methods can be used on `unomiTracker` object, although not all event types are supported by Unomi intergation.

## How to contribute

The source code is in the folder javascript with a package.json, the file to update is `analytics.js-integration-apache-unomi.js` apply your modification in this file then use the command `yarn build` to compile a new JS file.  
Then you can use the test page to try your changes `http://localhost:8181/tracker/index.html`.  
