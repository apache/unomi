# How to generate the REST API documentation

- Switch to the `rest-documentation` branch, a different branch is need so that we can add the proper `@Path` annotations on the endpoint and add the Maven plugin configuration 
for documentation generation. 
- Make sure that the changes from master are incorporated to the branch by performing a rebase: `git rebase master`.
- Run `mvn test`.
- The documentation should now be available via `target/miredot/index.html`