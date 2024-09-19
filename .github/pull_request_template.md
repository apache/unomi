PR Title format: 

    [UNOMI-XXX] Pull request title with JIRA reference

**Please** add a meaningful description for your change here

----

**Please** following this checklist to help us incorporate your contribution quickly and easily:

 - [ ] Make sure there is a [JIRA issue](https://issues.apache.org/jira/browse/UNOMI) filed 
       for the change (usually before you start working on it).  Trivial changes like typos do not 
       require a JIRA issue.  Your pull request should address just this issue, without pulling in other changes.
 - [ ] Format the pull request title like `[UNOMI-XXX] - Title of the pull request`
 - [ ] Provide integration tests for your changes, especially if you are changing the behavior of existing code or adding
       significant new parts of code.
 - [ ] Write a pull request description that is detailed enough to understand what the pull request does, how, and why. 
       Copy the description to the related JIRA issue
 - [ ] Run `mvn clean install -P integration-tests` to make sure basic checks pass. A more thorough check will be 
        performed on your pull request automatically.
 
Trivial changes like typos do not require a JIRA issue (javadoc, project build changes, small doc changes, comments...). 
 
If this is your first contribution, you have to read the [Contribution Guidelines](https://unomi.apache.org/contribute.html)

If your pull request is about ~20 lines of code you don't need to sign an [Individual Contributor License Agreement](https://www.apache.org/licenses/icla.pdf) 
if you are unsure please ask on the developers list.

To make clear that you license your contribution under the [Apache License Version 2.0, January 2004](http://www.apache.org/licenses/LICENSE-2.0)
you have to acknowledge this by using the following check-box.

 - [ ] I hereby declare this contribution to be licenced under the [Apache License Version 2.0, January 2004](http://www.apache.org/licenses/LICENSE-2.0)
