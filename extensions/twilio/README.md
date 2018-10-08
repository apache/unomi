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
        window.unomiTracker||(window.unomiTracker={}),function(){for(var e=[],r=["trackSubmit","trackClick","trackLink","trackForm","initialize","pageview","identify","reset","group","track","ready","alias","debug","page","once","off","on"],n=0;n<r.length;n++){var t=r[n];window.unomiTracker[t]=function(r){return function(){var n=Array.prototype.slice.call(arguments);return n.unshift(r),e.push(n),window.unomiTracker}}(t)}unomiTracker.load=function(e,r){var n=document.createElement("script");n.type="text/javascript",n.async=!0,n.src=r.url+"/tracker/javascript/unomi-tracker.js",n.addEventListener?n.addEventListener("load",function(r){"function"==typeof e&&e(r)},!1):n.onreadystatechange=function(){"complete"!=this.readyState&&"loaded"!=this.readyState||e(window.event)};var t=document.getElementsByTagName("script")[0];t.parentNode.insertBefore(n,t)},unomiTracker.load(function(r){for(unomiTracker.initialize({"Apache Unomi":r});e.length>0;){var n=e.shift(),t=n.shift();unomiTracker[t]&&unomiTracker[t].apply(unomiTracker,n)}},unomiOption),unomiTracker.page()}();
</script>
```

`window.unomiTracker` can be used to send additional events when needed.

Check analytics.js API [here](https://segment.com/docs/sources/website/analytics.js/). All methods can be used on `unomiTracker` object, although not all event types are supported by Unomi intergation.