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


Apache Unomi Web Tracker Javascript Library
===========================================

This is the web tracker for apache-unomi ( http://unomi.apache.org/ )

This package can be used in a Javascript application to interact with Apache Unomi.

## Getting started

Adds tracker to your app :

`npm add unomi-analytics`

Then 

```javascript
unomiTracker.initialize({
     'Apache Unomi': {
         scope: 'my-app',
         url: 'http://unomi:8181',
     }
 });
 
unomiTracker.ready(function() {
    console.log("Unomi context loaded - profile id : "+window.cxs.profileId + ", sessionId="+window.cxs.sessionId);
});
```

## Implicit page view event

In the initialize call, the tracker will generate an implicit page view event, which by default will be populated with 
the following information: 

```javascript
    window.digitalData.page = window.digitalData.page || {
        path: location.pathname + location.hash,
        pageInfo: {
            pageName: document.title,
            pageID : location.pathname + location.hash,
            pagePath : location.pathname + location.hash,
            destinationURL: location.href
        }
    }
```

Now if you want to provide your own custom page information for the initial page view, you can simply do it like this:

````javascript
    unomiTracker.initialize({
            scope: 'myScope',
            url: 'http://unomi:8181', // we use an empty URL to make it relative to this page.
            initialPageProperties: {
                path: path,
                pageInfo: {
                    destinationURL: location.href,
                    tags: ["tag1", "tag2", "tag3"],
                    categories: ["category1", "category2", "category3"]
                },
                interests: {
                    "interest1": 1,
                    "interest2": 2,
                    "interest3": 3
                }
            }
        });
````

Also note that the FIRST call to unomiTracker.page() will be IGNORED because of this initial page view. This is the 
way that the Analytics.js library handles it. So make sure you are aware of this when calling it. This is to avoid having
two page views on a single call and to be compatible with old versions that did use the explicit call.

## Sending events

Here are some examples of sending events :

```javascript
unomiTracker.page() // first call will be ignored as the initial page load is done in the initialize method

unomiTracker.identify({
    nickname: 'Amazing Grace',
    favoriteCompiler: 'A-0',
    industry: 'Computer Science'
});

unomiTracker.track('articleCompleted', {
    title: 'How to Create a Tracking Plan',
    course: 'Intro to Analytics'
});

```

As the Unomi Tracker uses the Analytics.JS API, you can find more information about it [here](https://segment.com/docs/sources/website/analytics.js/). 
All methods can be used on `unomiTracker` object, although not all event types are supported by Unomi integration.
