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

# Concepts

Apache Unomi gathers information about users actions, information that is processed and stored by Unomi services. The collected information can then be used to personalize content, derive insights on user behavior, categorize the user profiles into segments along user-definable dimensions or acted upon by algorithms.

## Items and types
Unomi structures the information it collects using the concept of `Item` which provides the base information (an identifier and a type) the context server needs to process and store the data. Items are persisted according to their type (structure) and identifier (identity). This base structure can be extended, if needed, using properties in the form of key-value pairs.

These properties are further defined by the `Item`’s type definition which explicits the `Item`’s structure and semantics. By defining new types, users specify which properties (including the type of values they accept) are available to items of that specific type.

Unomi defines default value types: `date`, `email`, `integer` and `string`, all pretty self-explanatory. While you can think of these value types as "primitive" types, it is possible to extend Unomi by providing additional value types.


Additionally, most items are also associated to a scope, which is a concept that Unomi uses to group together related items. A given scope is represented in Unomi by a simple string identifier and usually represents an application or set of applications from which Unomi gathers data, depending on the desired analysis granularity. In the context of web sites, a scope could, for example, represent a site or family of related sites being analyzed. Scopes allow clients accessing the context server to filter data to only see relevant data.

*Base `Item` structure:*

```json
{
  "itemType": <type of the item>,
  "scope": <scope>,
  "itemId": <item identifier>,
  "properties": <optional properties>
}
```

Some types can be dynamically defined at runtime by calling to the REST API while other extensions are done via Unomi plugins. Part of extending Unomi, therefore, is a matter of defining new types and specifying which kind of Unomi entity (e.g. profiles) they can be affected to. For example, the following JSON document can be passed to Unomi to declare a new property type identified (and named) `tweetNb`, tagged with the `social` tag, targeting profiles and using the `integer` value type.

*Example JSON type definition:*

```json
{
    "itemId": "tweetNb",
    "itemType": "propertyType",
    "metadata": {
        "id": "tweetNb",
        "name": "tweetNb"
    },
    "tags": ["social"],
    "target": "profiles",
    "type": "integer"
}
```


>Unomi defines a built-in scope (called `systemscope`) that clients can use to share data across scopes.

## Events
Users' actions are conveyed from clients to the context server using events. Of course, the required information depends on what is collected and users' interactions with the observed systems but events minimally provide a type, a scope and source and target items. Additionally, events are timestamped. Conceptually, an event can be seen as a sentence, the event's type being the verb, the source the subject and the target the object.


*Event structure:*

```json
{
    "eventType": <type of the event>,
    "scope": <scope of the event>,
    "source": <Item>,
    "target": <Item>,
    "properties": <optional properties>
}
```

Source and target can be any Unomi item but are not limited to them. In particular, as long as they can be described using properties and Unomi’s type mechanism and can be processed either natively or via extension plugins, source and target can represent just about anything. Events can also be triggered as part of Unomi’s internal processes for example when a rule is triggered.

Events are sent to Unomi from client applications using the JSON format and a typical page view event from a web site could look something like the following:

*Example page view event:*

```json
{
    "eventType": "view",
    "scope": "ACMESPACE",
    "source": {
        "itemType": "site",
        "scope": "ACMESPACE",
        "itemId": "c4761bbf-d85d-432b-8a94-37e866410375"
    },
    "target": {
        "itemType": "page",
        "scope": "ACMESPACE",
        "itemId": "b6acc7b3-6b9d-4a9f-af98-54800ec13a71",
        "properties": {
            "pageInfo": {
            "pageID": "b6acc7b3-6b9d-4a9f-af98-54800ec13a71",
            "pageName": "Home",
            "pagePath": "/sites/ACMESPACE/home",
            "destinationURL": "http://localhost:8080/sites/ACMESPACE/home.html",
            "referringURL": "http://localhost:8080/",
            "language": "en"
        },
        "category": {},
        "attributes": {}
      }
    }
}
```

## Profiles
By processing events, Unomi progressively builds a picture of who the user is and how they behave. This knowledge is embedded in `Profile` object. A profile is an `Item` with any number of properties and optional segments and scores. Unomi provides default properties to cover common data (name, last name, age, email, etc.) as well as default segments to categorize users. Unomi users are, however, free and even encouraged to create additional properties and segments to better suit their needs.

Contrary to other Unomi items, profiles are not part of a scope since we want to be able to track the associated user across applications. For this reason, data collected for a given profile in a specific scope is still available to any scoped item that accesses the profile information.

It is interesting to note that there is not necessarily a one to one mapping between users and profiles as users can be captured across applications and different observation contexts. As identifying information might not be available in all contexts in which data is collected, resolving profiles to a single physical user can become complex because physical users are not observed directly. Rather, their portrait is progressively patched together and made clearer as Unomi captures more and more traces of their actions. Unomi will merge related profiles as soon as collected data permits positive association between distinct profiles, usually as a result of the user performing some identifying action in a context where the user hadn’t already been positively identified.

## Sessions
A session represents a time-bounded interaction between a user (via their associated profile) and a Unomi-enabled application. A session represents the sequence of actions the user performed during its duration. For this reason, events are associated with the session during which they occurred. In the context of web applications, sessions are usually linked to HTTP sessions.

# Extending Unomi via plugins
Unomi is architected so that users can provided extensions in the form of plugins.

## Types vs. instances
Several extension points in Unomi rely on the concept of type: the extension defines a prototype for what the actual items will be once parameterized with values known only at runtime. This is similar to the concept of classes in object-oriented programming: types define classes, providing the expected structure and which fields are expected to be provided at runtime, that are then instantiated when needed with actual values.

## Plugin structure
Being built on top of Apache Karaf, Unomi leverages OSGi to support plugins. A Unomi plugin is, thus, an OSGi bundle specifying some specific metadata to tell Unomi the kind of entities it provides. A plugin can provide the following entities to extend Unomi, each with its associated definition (as a JSON file), located in a specific spot within the `META-INF/cxs/` directory of the bundle JAR file:


| Entity | Location in `cxs` directory |
| -------- | -------- |
| ActionType   | actions  |
| ConditionType | conditions |
| Persona | personas |
| PropertyMergeStrategyType | mergers |
| PropertyType | properties then profiles or sessions subdirectory then `<category name>` directory |
| Rule | rules |
| Scoring | scorings |
| Segment | segments |
| Tag | tags |
| ValueType | values |

[Blueprint](http://aries.apache.org/modules/blueprint.html) is used to declare what the plugin provides and inject any required dependency. The Blueprint file is located, as usual, at `OSGI-INF/blueprint/blueprint.xml` in the bundle JAR file.

The plugin otherwise follows a regular maven project layout and should depend on the Unomi API maven artifact:

```xml
<dependency>
    <groupId>org.apache.unomi</groupId>
    <artifactId>unomi-api</artifactId>
    <version>...</version>
</dependency>
```

Some plugins consists only of JSON definitions that are used to instantiate the appropriate structures at runtime while some more involved plugins provide code that extends Unomi in deeper ways.

In both cases, plugins can provide more that one type of extension. For example, a plugin could provide both `ActionType`s and `ConditionType`s.

## Extension points

### ActionType
`ActionType`s define new actions that can be used as consequences of Rules being triggered. When a rule triggers, it creates new actions based on the event data and the rule internal processes, providing values for parameters defined in the associated `ActionType`. Example actions include: “Set user property x to value y” or “Send a message to service x”.

### ConditionType
`ConditionType`s define new conditions that can be applied to items (for example to decide whether a rule needs to be triggered or if a profile is considered as taking part in a campaign) or to perform queries against the stored Unomi data. They may be implemented in Java when attempting to define a particularly complex test or one that can better be optimized by coding it. They may also be defined as combination of other conditions. A simple condition  could be: “User is male”, while a more generic condition with parameters may test whether a given property has a specific value: “User property x has value y”.

### Persona
A persona is a "virtual" profile used to represent categories of profiles, and may also be used to test how a personalized experience would look like using this virtual profile. A persona can define predefined properties and sessions. Persona definition make it possible to “emulate” a certain type of profile, e.g : US visitor, non-US visitor, etc.

### PropertyMergeStrategyType
A strategy to resolve how to merge properties when merging profile together.

### PropertyType
Definition for a profile or session property, specifying how possible values are constrained, if the value is multi-valued (a vector of values as opposed to a scalar value). `PropertyType`s can also be categorized using tags or file system structure, using sub-directories to organize definition files.

### Rule
`Rule`s are conditional sets of actions to be executed in response to incoming events. Triggering of rules is guarded by a condition: the rule is only triggered if the associated condition is satisfied. That condition can test the event itself, but also the profile or the session.  Once a rule triggers, a list of actions can be performed as consequences. Also, when rules trigger, a specific event is raised so that other parts of Unomi can react accordingly.

### Scoring
`Scoring`s are set of conditions associated with a value to assign to profiles when matching so that the associated users can be scored along that dimension. Each scoring element is evaluated and matching profiles' scores are incremented with the associated value.

### Segments
`Segment`s represent dynamically evaluated groups of similar profiles in order to categorize the associated users. To be considered part of a given segment, users must satisfies the segment’s condition. If they match, users are automatically added to the segment. Similarly, if at any given point during, they cease to satisfy the segment’s condition, they are automatically removed from it.

### Tag
`Tag`s are simple labels that are used to classify all other objects inside Unomi. 

### ValueType
Definition for values that can be assigned to properties ("primitive" types).

## Other Unomi entities

### UserList
User list are simple static lists of users. The associated profile stores the lists it belongs to in a specific property.

### Goal
Goals represent tracked activities / actions that can be accomplished by site (or more precisely scope) visitors. These are tracked in general because they relate to specific business objectives or are relevant to measure site/scope performance.

Goals can be defined at the scope level or in the context of a particular `Campaign`. Either types of goals behave exactly the same way with the exception of two notable differences:
 - duration: scope-level goals are considered until removed while campaign-level goals are only considered for the campaign duration
 - audience filtering: any visitor is considered for scope-level goals while campaign-level goals only consider visitors who match the campaign's conditions

### Campaign
A goal-oriented, time-limited marketing operation that needs to be evaluated for return on investment performance by tracking the ratio of visits to conversions.
