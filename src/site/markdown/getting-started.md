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

# Getting started with Unomi

We will first get you up and running with an example. We will then lift the corner of the cover somewhat and explain in greater details what just happened.

## Prerequisites
This document assumes that you are already familiar with Unomi's [concepts](concepts.html). On the technical side, we also assume working knowledge of [git](https://git-scm.com/) to be able to retrieve the code for Unomi and the example. Additionnally, you will require a working Java 7 or above install. Refer to http://www.oracle.com/technetwork/java/javase/ for details on how to download and install Java SE 7 or greater.

## Running Unomi

### Building Unomi

1. Get the code: `git clone https://git-wip-us.apache.org/repos/asf/incubator-unomi.git`
2. Build and install according to the [instructions](building-and-deploying.html) and install Unomi.

### Start Unomi
Start Unomi according to the [instructions](building-and-deploying.html#Deploying_the_generated_package). Once you have Karaf running,
 you should wait until you see the following messages on the Karaf console:

```
Initializing user list service endpoint...
Initializing geonames service endpoint...
Initializing segment service endpoint...
Initializing scoring service endpoint...
Initializing campaigns service endpoint...
Initializing rule service endpoint...
Initializing profile service endpoint...
Initializing cluster service endpoint...
```

This indicates that all the Unomi services are started and ready to react to requests. You can then open a browser and go to `http://localhost:8181/cxs` to see the list of
available RESTful services or retrieve an initial context at `http://localhost:8181/context.json` (which isn't very useful at this point).

### Building the tweet button sample
In your local copy of the Unomi repository and run:

```
cd samples/tweet-button-plugin
mvn clean install
```

This will compile and create the OSGi bundle that can be deployed on Unomi to extend it.

### Deploying the tweet button sample
In standard Karaf fashion, you will need to copy the sample bundle to your Karaf `deploy` directory.

If you are using the packaged version of Unomi (as opposed to deploying it to your own Karaf version), you can simply run, assuming your current directory is `samples/tweet-button-plugin` and that you uncompressed the archive in the directory it was created:

```
cp target/tweet-button-plugin-1.0.0-incubating-SNAPSHOT.jar ../../package/target/unomi-1.0.0-incubating-SNAPSHOT/deploy
```


### Testing the sample
You can now go to http://localhost:8181/index.html to test the sample code. The page is very simple, you will see a Twitter button, which, once clicked, will open a new window to tweet about the current page.  The original page should be updated with the new values of the properties coming from Unomi. Additionnally, the raw JSON response is displayed.

We will now explain in greater details some concepts and see how the example works.

## Interacting with the context server
There are essentially two modalities to interact with the context server, reflecting different types of Unomi users: context server clients and context server integrators.

**Context server clients** are usually web applications or content management systems. They interact with Unomi by providing raw, uninterpreted contextual data in the form of events and associated metadata. That contextual data is then processed by the context server to be fed to clients once actionable. In that sense context server clients are both consumers and producers of contextual data. Context server clients will mostly interact with Unomi using a single entry point called the `ContextServlet`, requesting context for the current user and providing any triggered events along the way.

On the other hand, **context server integrators** provide ways to feed more structured data to the context server either to integrate with third party services or to provide analysis of the uninterpreted data provided by context server clients. Such integration will mostly be done using Unomi's API either directly using Unomi plugins or via the provided REST APIs. However, access to REST APIs is restricted due for security reasons, requiring privileged access to the Unomi server, making things a little more complex to set up.

For simplicity's sake, this document will focus solely on the first use case and will interact only with the context servlet.

## Retrieving context information from Unomi using the context servlet
Unomi provides two ways to retrieve context: either as a pure JSON object containing strictly context information or as a couple of JSON objects augmented with javascript functions that can be used to interact with the Unomi server using the `<context server base URL>/context.json` or `<context server base URL>/context.js` URLs, respectively.

Below is an example of asynchronously loading the initial context using the javascript version, assuming a default Unomi install running on `http://localhost:8181`:

```javascript
// Load context from Unomi asynchronously
(function (document, elementToCreate, id) {
    var js, fjs = document.getElementsByTagName(elementToCreate)[0];
    if (document.getElementById(id)) return;
    js = document.createElement(elementToCreate);
    js.id = id;
    js.src = 'http://localhost:8181/context.js';
    fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'context'));

```

This initial context results in a javascript file providing some functions to interact with the context server from javascript along with two objects: a `cxs` object containing
information about the context for the current user and a `digitalData` object that is injected into the browser’s `window` object (leveraging the
[Customer Experience Digital Data Layer](http://www.w3.org/2013/12/ceddl-201312.pdf) standard). Note that this last object is not under control of the context server and clients
 are free to use it or not. Our example will not make use of it.

On the other hand, the `cxs` top level object contains interesting contextual information about the current user:

```json
{
  "profileId":<identifier of the profile associated with the current user>,
  "sessionId":<identifier of the current user session>,
  "profileProperties":<requested profile properties, if any>,
  "sessionProperties":<requested session properties, if any>,
  "profileSegments":<segments the profile is part of if requested>,
  "filteringResults":<result of the evaluation of personalization filters>,
  "trackedConditions":<tracked conditions in the source page, if any>
}
```

We will look at the details of the context request and response later.

# Example

## Overview
We will examine how a simple HTML page can interact with Unomi to enrich a user's profile. The use case we will follow is a rather simple one: we want to react to Twitter events by associating information to their profile. We will record the number of times the user tweeted (as a `tweetNb` profile integer property) as well as the URLs they tweeted from (as a `tweetedFrom` multi-valued string profile property). We will accomplish this using a simple HTML page on which we position a standard "Tweet" button. A javascript script will use the Twitter API to react to clicks on this button and update the user profile using a `ContextServlet` request triggering a custom event. This event will, in turn, trigger a Unomi action on the server implemented using a Unomi plugin, a standard extension point for the server.

## HTML page
The code for the HTML page with our Tweet button can be found at https://github.com/apache/incubator-unomi/blob/master/wab/src/main/webapp/index.html.

This HTML page is fairly straightforward: we create a tweet button using the Twitter API while a Javascript script performs the actual logic.

## Javascript

Globally, the script loads both the twitter widget and the initial context asynchronously (as shown previously). This is accomplished using fairly standard javascript code and we won't look at it here. Using the Twitter API, we react to the `tweet` event and call the Unomi server to update the user's profile with the required information, triggering a custom `tweetEvent` event. This is accomplished using a `contextRequest` function which is an extended version of a classic `AJAX` request:

```javascript
function contextRequest(successCallback, errorCallback, payload) {
    var data = JSON.stringify(payload);
    // if we don't already have a session id, generate one
    var sessionId = cxs.sessionId || generateUUID();
    var url = 'http://localhost:8181/context.json?sessionId=' + sessionId;
    var xhr = new XMLHttpRequest();
    var isGet = data.length < 100;
    if (isGet) {
        xhr.withCredentials = true;
        xhr.open("GET", url + "&payload=" + encodeURIComponent(data), true);
    } else if ("withCredentials" in xhr) {
        xhr.open("POST", url, true);
        xhr.withCredentials = true;
    } else if (typeof XDomainRequest != "undefined") {
        xhr = new XDomainRequest();
        xhr.open("POST", url);
    }
    xhr.onreadystatechange = function () {
        if (xhr.readyState != 4) {
            return;
        }
        if (xhr.status == 200) {
            var response = xhr.responseText ? JSON.parse(xhr.responseText) : undefined;
            if (response) {
                cxs.sessionId = response.sessionId;
                successCallback(response);
            }
        } else {
            console.log("contextserver: " + xhr.status + " ERROR: " + xhr.statusText);
            if (errorCallback) {
                errorCallback(xhr);
            }
        }
    };
    xhr.setRequestHeader("Content-Type", "text/plain;charset=UTF-8"); // Use text/plain to avoid CORS preflight
    if (isGet) {
        xhr.send();
    } else {
        xhr.send(data);
    }
}
```

There are a couple of things to note here:

 - If we specify a payload, it is expected to use the JSON format so we `stringify` it and encode it if passed as a URL parameter in a `GET` request.
 - We need to make a [`CORS`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS)  request since the Unomi server is most likely not running on the same host than the one from which the request originates. The specific details are fairly standard and we will not explain them here.
 - We need to either retrieve (from the initial context we retrieved previously using `cxs.sessionId`) or generate a session identifier for our request since Unomi currently requires one.
 - We're calling the `ContextServlet` using the default install URI, specifying the session identifier:  `http://localhost:8181/context.json?sessionId=' + sessionId`. This URI requests context from Unomi, resulting in an updated `cxs` object in the javascript global scope. The context server can reply to this request either by returning a JSON-only object containing solely the context information as is the case when the requested URI is `context.json`. However, if the client requests `context.js` then useful functions to interact with Unomi are added to the `cxs` object in addition to the context information as depicted above.
 - We don't need to provide any authentication at all to interact with this part of Unomi since we only have access to read-only data (as well as providing events as we shall see later on). If we had been using the REST API, we would have needed to provide authentication information as well.

### Context request and response structure
The interesting part, though, is the payload. This is where we provide Unomi with contextual information as well as ask for data in return. This allows clients to specify which type of information they are interested in getting from the context server as well as specify incoming events or content filtering or property/segment overrides for personalization or impersonation. This conditions what the context server will return with its response.

Let's look at the context request structure:

```json
{
    source: <Item source of the context request>,
    events: <optional array of triggered events>,
    requiredProfileProperties: <optional array of property identifiers>,
    requiredSessionProperties: <optional array of property identifiers>,
    filters: <optional array of filters to evaluate>,
    segmentOverrides: <optional array of segment identifiers>,
    profilePropertiesOverrides: <optional map of property name / value pairs>,
    sessionPropertiesOverrides: <optional map of property name / value pairs>,
    requiresSegments: <boolean, whether to return the associated segments>
}
```

We will now look at each part in greater details.

#### Source
A context request payload needs to at least specify some information about the source of the request in the form of an `Item` (meaning identifier, type and scope plus any additional properties we might have to provide), via the `source` property of the payload. Of course the more information can be provided about the source, the better.

#### Filters
A client wishing to perform content personalization might also specify filtering conditions to be evaluated by the context server so that it can tell the client whether the content associated with the filter should be activated for this profile/session. This is accomplished by providing a list of filter definitions to be evaluated by the context server via the `filters` field of the payload. If provided, the evaluation results will be provided in the `filteringResults` field of the resulting `cxs` object the context server will send.

#### Overrides
It is also possible for clients wishing to perform user impersonation to specify properties or segments to override the proper ones so as to emulate a specific profile, in which case the overridden value will temporarily replace the proper values so that all rules will be evaluated with these values instead of the proper ones. The `segmentOverrides` (array of segment identifiers), `profilePropertiesOverrides` and `sessionPropertiesOverrides` (maps of property name and associated object value) fields allow to provide such information. Providing such overrides will, of course, impact content filtering results and segments matching for this specific request.

#### Controlling the content of the response
The clients can also specify which information to include in the response by setting the `requiresSegments` property to true if segments the current profile matches should be returned or provide an array of property identifiers for `requiredProfileProperties` or `requiredSessionProperties` fields to ask the context server to return the values for the specified profile or session properties, respectively. This information is provided by the `profileProperties`, `sessionProperties` and `profileSegments` fields of the context server response.

Additionally, the context server will also returns any tracked conditions associated with the source of the context request. Upon evaluating the incoming request, the context server will determine if there are any rules marked with the `trackedCondition` tag and which source condition matches the source of the incoming request and return these tracked conditions to the client. The client can use these tracked conditions to learn that the context server can react to events matching the tracked condition and coming from that source. This is, in particular, used to implement form mapping (a solution that allows clients to update user profiles based on values provided when a form is submitted).

#### Events
Finally, the client can specify any events triggered by the user actions, so that the context server can process them, via the `events` field of the context request.

#### Default response
If no payload is specified, the context server will simply return the minimal information deemed necessary for client applications to properly function: profile identifier, session identifier and any tracked conditions that might exist for the source of the request.

### Context request for our example
Now that we've seen the structure of the request and what we can expect from the context response, let's examine the request our component is doing.

In our case, our `source` item looks as follows: we specify a scope for our application (`unomi-tweet-button-sample`), specify that the item type (i.e. the kind of element that is the source of our event) is a `page` (which corresponds, as would be expected, to a web page), provide an identifier (in our case, a Base-64 encoded version of the page's URL) and finally, specify extra properties (here, simply a `url` property corresponding to the page's URL that will be used when we process our event in our Unomi extension).

```javascript
var scope = 'unomi-tweet-button-sample';
var itemId = btoa(window.location.href);
var source = {
    itemType: 'page',
    scope: scope,
    itemId: itemId,
    properties: {
        url: window.location.href
    }
};
```

We also specify that we want the context server to return the values of the `tweetNb` and `tweetedFrom` profile properties in its response. Finally, we provide a custom event of type `tweetEvent` with associated scope and source information, which matches the source of our context request in this case.

```javascript
var contextPayload = {
    source: source,
    events: [
        {
            eventType: 'tweetEvent',
            scope: scope,
            source: source
        }
    ],
    requiredProfileProperties: [
        'tweetNb',
        'tweetedFrom'
    ]
};
```

The `tweetEvent` event type is not defined by default in Unomi. This is where our Unomi plugin comes into play since we need to tell Unomi how to react when it encounters such events.

### Unomi plugin overview

In order to react to `tweetEvent` events, we will define a new Unomi rule since this is exactly what Unomi rules are supposed to do. Rules are guarded by conditions and if these
 conditions match, the associated set of actions will be executed. In our case, we want our new
 [`incrementTweetNumber`](https://github.com/apache/incubator-unomi/blob/master/samples/tweet-button-plugin/src/main/resources/META-INF/cxs/rules/incrementTweetNumber.json) rule to only react to `tweetEvent` events and
  we want it to perform the profile update accordingly: create the property types for our custom properties if they don't exist and update them. To do so, we will create a
  custom
  [`incrementTweetNumberAction`](https://github.com/apache/incubator-unomi/blob/master/samples/tweet-button-plugin/src/main/resources/META-INF/cxs/actions/incrementTweetNumberAction.json) action that will be triggered any time our rule matches. An action is some custom code that is deployed in the context server and can access the
  Unomi API to perform what it is that it needs to do.


### Rule definition
Let's look at how our custom [`incrementTweetNumber`](https://github.com/apache/incubator-unomi/blob/master/samples/tweet-button-plugin/src/main/resources/META-INF/cxs/rules/incrementTweetNumber.json) rule is defined:

```json
{
  "metadata": {
    "id": "smp:incrementTweetNumber",
    "name": "Increment tweet number",
    "description": "Increments the number of times a user has tweeted after they click on a tweet button"
  },
  "raiseEventOnlyOnceForSession": false,
  "condition": {
    "type": "eventTypeCondition",
    "parameterValues": {
      "eventTypeId": "tweetEvent"
    }
  },
  "actions": [
    {
      "type": "incrementTweetNumberAction",
      "parameterValues": {}
    }
  ]
}
```

Rules define a metadata section where we specify the rule name, identifier and description.

When rules trigger, a specific event is raised so that other parts of Unomi can react to it accordingly. We can control how that event should be raised. Here we specify that the event should be raised each time the rule triggers and not only once per session by setting `raiseEventOnlyOnceForSession` to `false`, which is not strictly required since that is the default. A similar setting (`raiseEventOnlyOnceForProfile`) can be used to specify that the event should only be raised once per profile if needed.

We could also specify a priority for our rule in case it needs to be executed before other ones when similar conditions match. This is accomplished using the `priority` property. We're using the default priority here since we don't have other rules triggering on `tweetEvent`s and don't need any special ordering.

We then tell Unomi which condition should trigger the rule via the `condition` property. Here, we specify that we want our rule to trigger on an `eventTypeCondition` condition. Unomi can be extended by adding new condition types that can enrich how matching or querying is performed. The condition type definition file specifies which parameters are expected for our condition to be complete. In our case, we use the built-in event type condition that will match if Unomi receives an event of the type specified in the condition's `eventTypeId` parameter value: `tweetEvent` here.

Finally, we specify a list of actions that should be performed as consequences of the rule matching. We only need one action of type `incrementTweetNumberAction` that doesn't require any parameters.

### Action definition
Let's now look at our custom [`incrementTweetNumberAction`](https://github.com/apache/incubator-unomi/blob/master/samples/tweet-button-plugin/src/main/resources/META-INF/cxs/actions/incrementTweetNumberAction.json) action type definition:

```json
{
  "id": "incrementTweetNumberAction",
  "actionExecutor": "incrementTweetNumber",
  "tags": [
    "event"
  ],
  "parameters": []
}
```

We specify the identifier for the action type, a list of tags if needed: here we say that our action is a consequence of events using the `event` tag. Our actions does not require any parameters so we don't define any.

Finally, we provide a mysterious `actionExecutor` identifier: `incrementTweetNumber`.

### Action executor definition
The action executor references the actual implementation of the action as defined in our [blueprint definition](https://github.com/apache/incubator-unomi/blob/master/samples/tweet-button-plugin/src/main/resources/OSGI-INF/blueprint/blueprint.xml):

```xml
<blueprint xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
           xsi:schemaLocation="http://www.osgi.org/xmlns/blueprint/v1.0.0 http://www.osgi.org/xmlns/blueprint/v1.0.0/blueprint.xsd">

    <reference id="profileService" interface="org.apache.unomi.api.services.ProfileService"/>

    <!-- Action executor -->
    <service id="incrementTweetNumberAction" auto-export="interfaces">
        <service-properties>
            <entry key="actionExecutorId" value="incrementTweetNumber"/>
        </service-properties>
        <bean class="org.apache.unomi.examples.unomi_tweet_button_plugin.actions.IncrementTweetNumberAction">
            <property name="profileService" ref="profileService"/>
        </bean>
    </service>
</blueprint>
```

In standard Blueprint fashion, we specify that we will need the `profileService` defined by Unomi and then define a service of our own to be exported for Unomi to use. Our service specifies one property: `actionExecutorId` which matches the identifier we specified in our action definition. We then inject the profile service in our executor and we're done for the configuration side of things!

### Action executor implementation
Our action executor definition specifies that the bean providing the service is implemented in the [`org.apache.unomi.samples.tweet_button_plugin.actions
.IncrementTweetNumberAction`](https://github.com/apache/incubator-unomi/blob/master/samples/tweet-button-plugin/src/main/java/org/apache/unomi/samples/tweet_button_plugin/actions/IncrementTweetNumberAction.java) class. This class implements the Unomi `ActionExecutor` interface which provides a single `int execute(Action action, Event event)` method: the executor gets the action instance to execute along with the event that triggered it, performs its work and returns an integer status corresponding to what happened as defined by public constants of the `EventService` interface of Unomi: `NO_CHANGE`, `SESSION_UPDATED` or `PROFILE_UPDATED`.

Let's now look at the implementation of the method:

```java
final Profile profile = event.getProfile();
Integer tweetNb = (Integer) profile.getProperty(TWEET_NB_PROPERTY);
List<String> tweetedFrom = (List<String>) profile.getProperty(TWEETED_FROM_PROPERTY);

if (tweetNb == null || tweetedFrom == null) {
    // create tweet number property type
    PropertyType propertyType = new PropertyType(new Metadata(event.getScope(), TWEET_NB_PROPERTY, TWEET_NB_PROPERTY, "Number of times a user tweeted"));
    propertyType.setValueTypeId("integer");
    service.createPropertyType(propertyType);

    // create tweeted from property type
    propertyType = new PropertyType(new Metadata(event.getScope(), TWEETED_FROM_PROPERTY, TWEETED_FROM_PROPERTY, "The list of pages a user tweeted from"));
    propertyType.setValueTypeId("string");
    propertyType.setMultivalued(true);
    service.createPropertyType(propertyType);

    tweetNb = 0;
    tweetedFrom = new ArrayList<>();
}

profile.setProperty(TWEET_NB_PROPERTY, tweetNb + 1);
final String sourceURL = extractSourceURL(event);
if (sourceURL != null) {
    tweetedFrom.add(sourceURL);
}
profile.setProperty(TWEETED_FROM_PROPERTY, tweetedFrom);

return EventService.PROFILE_UPDATED;
```

 It is fairly straightforward: we retrieve the profile associated with the event that triggered the rule and check whether it already has the properties we are interested in. If not, we create the associated property types and initialize the property values.

>Note that it is not an issue to attempt to create the same property type multiple times as Unomi will not add a new property type if an identical type already exists.

Once this is done, we update our profile with the new property values based on the previous values and the metadata extracted from the event using the `extractSourceURL` method which uses our `url` property that we've specified for our event source. We then return that the profile was updated as a result of our action and Unomi will properly save it for us when appropriate. That's it!

For reference, here's the `extractSourceURL` method implementation:

```java
private String extractSourceURL(Event event) {
    final Item sourceAsItem = event.getSource();
    if (sourceAsItem instanceof CustomItem) {
        CustomItem source = (CustomItem) sourceAsItem;
        final String url = (String) source.getProperties().get("url");
        if (url != null) {
            return url;
        }
    }

    return null;
}
```

# Conclusion
We have seen a simple example how to interact with Unomi using a combination of client-side code and Unomi plugin. Hopefully, this provided an introduction to the power of what Unomi can do and how it can be extended to suit your needs.

# Annex
Here is an overview of how Unomi processes incoming requests to the `ContextServlet`.
![Unomi request overview](images/unomi-request.png)
