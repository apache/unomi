<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# Unomi Podling Maturity Assessment

## Overview

This is an assessment of the Unomi podling's maturity, meant to help inform
the decision (of the mentors, community, Incubator PMC and ASF Board of 
Directors) to graduate it as a top-level Apache project.

It is based on the ASF project maturity model at
https://community.apache.org/apache-way/apache-project-maturity-model.html

Maintaining such a file is a new, experimental idea as part of the continuous
improvement of the ASF incubation process. Unomi is the second podling where 
that happens.

## Status of this document
Updated 2017-07-19 with the latest status of the project.

## Overall assessment
Just introduced this report card, we will need to evaluate all the model's parts to see how we fare so far.

## Maturity model assessment 
Mentors and community members are encouraged to contribute to this 
and comment on it.

### Code

#### CD10
_The project produces Open Source software, for distribution to the public at no charge._

- [ OK ] : everything is hosted at the Apache Software Foundation
 
#### CD20
_The project's code is easily discoverable and publicly accessible._

- [ OK ] : it is linked from the main project site and accessible in the public ASF git repository

#### CD30
_The code can be built in a reproducible way using widely available standard tools._

- [ OK ] : Jenkins is configured on the project.

#### CD40
_The full history of the project's code is available via a source code control system, in a way that allows any released version to be recreated._

- [ OK ] : everything is in the ASF Git repository.

#### CD50
_The provenance of each line of code is established via the source code control system, in a reliable way based on strong authentication of the committer.
When third-party contributions are committed, commit messages provide reliable information about the code provenance._

- [ OK ] : Only ASF committers with CLAs may contribute to the project.

### Licenses and Copyright

#### LC10
_The code is released under the Apache License, version 2._0._ 

- [ OK ] : A LICENSE file is also at the root of the ASF Git repository

#### LC20
_Libraries that are mandatory dependencies of the project's code do not create more restrictions than the Apache License does._

- [ OK ] : NOTICE files contain all the information and has been reviewed on two releases already

#### LC30
_The libraries mentioned in LC20 are available as Open Source software._

- [ OK ] : See LC20

#### LC40
_Committers are bound by an Individual Contributor Agreement (the "Apache iCLA") that defines which code they are allowed to commit and how they need to identify code that is not their own._

- [ OK ] : All committers have registered iCLAs

#### LC50
_The copyright ownership of everything that the project produces is clearly defined and documented._

- [ ] : to be evaluated

### Releases

#### RE10
_Releases consist of source code, distributed using standard and open archive formats that are expected to stay readable in the long term._

- [ OK ] : Two releases have been produced so far and have been reviewed by IPMCs

#### RE20
_Releases are approved by the project's PMC (see CS10), in order to make them an act of the Foundation._

- [ OK ] : Happened for two major releases already

#### RE30
_Releases are signed and/or distributed along with digests that can be reliably used to validate the downloaded archives._

- [ OK ] : See releases here

#### RE40
_Convenience binaries can be distributed alongside source code but they are not Apache Releases -- they are just a convenience provided with no guarantee._

- [ OK ] : See the releases here

### Quality

#### QU10
_The project is open and honest about the quality of its code. Various levels of quality and maturity for various modules are natural and acceptable as long as they are clearly communicated._ 

- [ ] : to be evaluated

#### QU20
_The project puts a very high priority on producing secure software._

- [ ] : to be evaluated

#### QU30
_The project provides a well-documented channel to report security issues, along with a documented way of responding to them._

- [ ] : to be evaluated

#### QU40
_The project puts a high priority on backwards compatibility and aims to document any incompatible changes and provide tools and documentation to help users transition to new features._ 

- [ ] : to be evaluated

#### QU50
_The project strives to respond to documented bug reports in a timely manner._

- [ ] : to be evaluated

### Community

#### CO10
_The project has a well-known homepage that points to all the information required to operate according to this maturity model._

- [ OK ] : See the [project's home page](http://unomi.incubator.apache.org)

#### CO20
_The community welcomes contributions from anyone who acts in good faith and in a respectful manner and adds value to the project._ 

- [ ] : to be evaluated

#### CO30
_Contributions include not only source code, but also documentation, constructive bug reports, constructive discussions, marketing and generally anything that adds value to the project._

- [ ] : to be evaluated

#### CO40
_The community is meritocratic and over time aims to give more rights and responsibilities to contributors who add value to the project._

- [ ] : to be evaluated

#### CO50
_The way in which contributors can be granted more rights such as commit access or decision power is clearly documented and is the same for all contributors._

- [ ] : to be evaluated

#### CO60
_The community operates based on consensus of its members (see CS10) who have decision power. Dictators, benevolent or not, are not welcome in Apache projects._

- [ ] : to be evaluated

#### CO70
_The project strives to answer user questions in a timely manner._

- [ ] : to be evaluated

### Consensus Building

#### CS10
_The project maintains a public list of its contributors who have decision power -- the project's PMC (Project Management Committee) consists of those contributors._

- [ ] : to be evaluated

#### CS20
_Decisions are made by consensus among PMC members and are documented on the project's main communications channel. Community opinions are taken into account but the PMC has the final word if needed._

- [ ] : to be evaluated

#### CS30
_Documented voting rules are used to build consensus when discussion is not sufficient._ 

- [ ] : to be evaluated

#### CS40
_In Apache projects, vetoes are only valid for code commits and are justified by a technical explanation, as per the Apache voting rules defined in CS30._

- [ ] : to be evaluated

#### CS50
_All "important" discussions happen asynchronously in written form on the project's main communications channel. Offline, face-to-face or private discussions that affect the project are also documented on that channel._

- [ ] : to be evaluated

### Independence

#### IN10
_The project is independent from any corporate or organizational influence._

- [ ] : to be evaluated

#### IN20
_Contributors act as themselves as opposed to representatives of a corporation or organization._

- [ ] : to be evaluated
