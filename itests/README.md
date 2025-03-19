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

Apache Unomi Integration tests
=================================

## Information
You will likely run into situation where you need to wait for the execution of your test.  
To do so please avoid long Thread.sleep(10000) it tend to make the test unstable, prefer a shorter sleep that you will repeat.  
e.g:  
```java
boolean isDone = false;
while (!isDone) {
    importConfiguration = importConfigurationService.load(itemId);
    if (importConfiguration != null && importConfiguration.getStatus() != null) {
        isDone = importConfiguration.getStatus().equals(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS);
    }
    Thread.sleep(1000);
}
```

**NEVER** create dependencies between your test, even in the same class, the execution order is not guaranteed therefore you may not 
have what you expect and it could work fine on your machine but not on others.  

If possible clean what your test created at the end of its execution or at the very least make sure to use unique IDs  

When you need a service from Unomi to execute your test, you can add it to the BaseIT code:  
```java
@Before
public void waitForStartup() throws InterruptedException {
    // ...
    // init unomi services that are available once unomi:start have been called
    persistenceService=getOsgiService(PersistenceService.class, 600000);
}
``` 
This will ensure the service is available before starting the test.
## Running integration tests

You can run the integration tests along with the build by doing:

    mvn clean install -P integration-tests
    
from the project's root directory

### Search Engine Selection

Apache Unomi supports both ElasticSearch and OpenSearch as search engine backends. The integration tests can be configured to run against either engine:

```bash
# Run with ElasticSearch (default)
mvn clean install -P integration-tests

# Run with OpenSearch 
mvn clean install -P integration-tests -Duse.opensearch=true
```

When using OpenSearch, you might see log messages like:
```
[o.o.w.QueryGroupTask] QueryGroup _id can't be null
```
This is a known issue in OpenSearch 2.18 that doesn't affect functionality. You can track this issue at:
https://github.com/opensearch-project/OpenSearch/issues/16874

## Debugging integration tests

If you want to run the tests with a debugger, you can use the `it.karaf.debug` system property.
Here's an example:

    cd itests
    mvn clean install -Dit.karaf.debug=hold:true
    
The `hold:true` will tell the JVM to pause for you to connect a debugger. You can simply connect a remote debugger on $
port 5006 to debug the integration tests.

Here are the parameters supported by the `it.karaf.debug` property:

    hold:true - forces a wait for a remote debugger to connect 
    hold:false - continues even with no remote debugger connected
    port:XXXX allows to configure the binding port to XXXX
    
You can combine both parameters using a comma as a separator, as in the following example:

    mvn clean install -Dit.karaf.debug=hold:true,port=5006
    
## Running a single test

If you want to run a single test or single methods, following the instructions given here:
https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html

Here's an example:

    mvn clean install -Dit.karaf.debug=hold:true -Dit.test=org.apache.unomi.itests.graphql.GraphQLEventIT

To run a specific test method within a test class, you can use the # symbol followed by the method name:

    mvn clean install -Dit.test=org.apache.unomi.itests.ContextServletIT#testContextEndpointAuthentication

You can also use patterns to run multiple methods that match a pattern:

    mvn clean install -Dit.test=org.apache.unomi.itests.ContextServletIT#test*Authentication*

## Migration tests

Migration can now be tested, by reusing an ElasticSearch snapshot. 
The snapshot should be from a Unomi version where you want to start the migration from.

The snapshot is copied to the /target folder using a maven ant plugin:

    <plugin>
        <artifactId>maven-antrun-plugin</artifactId>
        <version>1.8</version>
        <executions>
            <execution>
                <phase>generate-resources</phase>
                <configuration>
                <tasks>
                    <unzip src="${project.basedir}/src/test/resources/migration/snapshots_repository.zip" dest="${project.build.directory}" />
                </tasks>
                </configuration>
                <goals>
                    <goal>run</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

Also the ElasticSearch maven plugin is configured to allow this snapshot repository using conf:

    <path.repo>${project.build.directory}/snapshots_repository</path.repo>

Now that migration accept configuration file we can provide it, this allows to avoid the migration process to prompt questions (in BaseIT configuration):

    replaceConfigurationFile("etc/org.apache.unomi.migration.cfg", new File("src/test/resources/migration/org.apache.unomi.migration.cfg")),

The config should contain all the required prop for the migration you want to do, example:

    esAddress = http://localhost:9400
    httpClient.trustAllCertificates = true
    indexPrefix = context

Then in the first Test of the suite you can restore the Snapshot and run the migration cmd, like this:

```java
public class Migrate16xTo200IT extends BaseIT {

    @Override
    @Before
    public void waitForStartup() throws InterruptedException {

        // Restore snapshot from 1.6.x
        try (CloseableHttpClient httpClient = HttpUtils.initHttpClient(true)) {
            // Create snapshot repo
            HttpUtils.executePutRequest(httpClient, "http://localhost:9400/_snapshot/snapshots_repository/", resourceAsString("migration/create_snapshots_repository.json"), null);
            // Get snapshot, insure it exists
            String snapshot = HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/_snapshot/snapshots_repository/snapshot_1.6.x", null);
            if (snapshot == null || !snapshot.contains("snapshot_1.6.x")) {
                throw new RuntimeException("Unable to retrieve 1.6.x snapshot for ES restore");
            }
            // Restore the snapshot
            HttpUtils.executePostRequest(httpClient, "http://localhost:9400/_snapshot/snapshots_repository/snapshot_1.6.x/_restore?wait_for_completion=true", "{}", null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Do migrate the data set
        executeCommand("unomi:migrate 1.6.0 true");
        // Call super for starting Unomi and wait for the complete startup
        super.waitForStartup();
    }

    @After
    public void cleanup() throws InterruptedException {
        // Do some cleanup for next tests
    }

    @Test
    public void checkMigratedData() throws Exception {
        // call Unomi services to check the migrated data is correct.
    }
}
``` 

### How to update a migration test ElasticSearch Snapshot ?

In the following example we want to modify the snapshot: `snapshot_1.6.x`.
This snapshot has been done on Unomi 1.6.x using ElasticSearch 7.11.0. 
So we will set up locally those servers in the exact same versions.
(For now just download them and do not start them yet.)

To ease the migration, you can run the docker image of ElasticSearch 7.11.0 with the following command:

    docker run -p 9200:9200 -e path.repo="/tmp/snapshots_repository"  -e discovery.type=single-node docker.elastic.co/elasticsearch/elasticsearch:7.11.0

Note that the path.repo is set to `/tmp/snapshots_repository` so you can use this path to store the snapshot repository.

First we need to extract the zip of the snapshot repository from the test resources:

    /src/test/resources/migration/snapshots_repository.zip
    
>    If you use docker, you can copy the zip file to the docker container using the following command:
>    docker cp src/test/resources/migration/snapshots_repository.zip <container_id>:/tmp/snapshots_repository.zip

Then unzip it to the path you want to use as snapshot repository.

In my case I unzip it to:

    /servers/elasticsearch-7.11.0/

>   For docker unzip in the `/tmp` folder.

So I have the following folders structure:

    /servers/elasticsearch-7.11.0/snapshots_repository/snapshots

Now we need to configure our ElasticSearch server to allow this path as repo, edit the `elasticsearch.yml` to add this:

    path:
        repo:
            - /servers/elasticsearch-7.11.0/snapshots_repository

> This step is not required for docker.

Start ElasticSearch server.
Now we have to add the snapshot repository, do the following request on your ElasticSearch instance:

    PUT /_snapshot/snapshots_repository/
    {
        "type": "fs",
        "settings": {
            "location": "snapshots"
        }
    }

Now we need to restore the snapshot we want to modify, 
but first let's try to see if the snapshot with the id `snapshot_1.6.x` correctly exists:

    GET /_snapshot/snapshots_repository/snapshot_1.6.x

If the snapshot exists we can restore it:

    POST /_snapshot/snapshots_repository/snapshot_1.6.x/_restore?wait_for_completion=true
    {}

At the end of the previous request ElasticSearch should be ready and our Unomi snapshot is restored to version `1.6.x`.
Now make sure your Unomi server is correctly configured to connect to your running ElasticSearch, then start the Unomi server.
In my case it's Unomi version 1.6.0.

Once Unomi started you can perform all the operations you want to be able to add the required data to the next snapshot, like:
- creating new events
- creating new profiles with new data to be migrated
- create rules/segments etc ...
- anything you want to be part of the new snapshot.

(NOTE: that it is important to add new data to the existing snapshot, but try to not removing things, 
they are probably used by the actual migration tests already.)

Once you data updated we need to recreate the snapshot, first we delete the old snapshot:

    DELETE /_snapshot/snapshots_repository/snapshot_1.6.x

Then we recreate it:

    PUT /_snapshot/snapshots_repository/snapshot_1.6.x

Once the process finished (check the ElasticSearch logs to see that the snapshot is correctly created), 
we need to remove the snapshot repository from our local ElasticSearch

    DELETE /_snapshot/snapshots_repository

And the final step is, zipping the new version of the snapshot repository and replace it in the test resources:

    zip -r snapshots_repository.zip /servers/elasticsearch-7.11.0/snapshots_repository
    cp /servers/elasticsearch-7.11.0/snapshots_repository.zip src/test/resources/migration/snapshots_repository.zip

> In case you are using docker, do zip in the container and use `docker cp` to get the zip file from the docker container.

Now you can modify the migration test class to test that your added data in 1.6.x is correctly migrated in 2.0.0

# Known issues

In the OpenSearch test logs, you will see a lot of lines that look like this : 

    opensearch> [2024-12-31T15:33:14,652][WARN ][o.o.w.QueryGroupTask     ] [f3200971b164] QueryGroup _id can't be null, It should be set before accessing it. This is abnormal behaviour

This is due to a bug in OpenSearch 2.18 but it has no impact on the actual functionality. You can track this bug here:

    https://github.com/opensearch-project/OpenSearch/issues/16874

## Karaf Tools

The `kt.sh` script (short for "Karaf Tools") provides convenient utilities for working with Karaf logs and directories during integration testing. Since Karaf test directories are created with unique UUIDs for each test run, this script helps locate and work with the latest test instance.

### Usage

```bash
./kt.sh COMMAND [ARGS]
```

### Available Commands

| Command      | Alias | Description                                           |
|-------------|-------|-------------------------------------------------------|
| `log`       | `l`   | View the latest Karaf log file using less            |
| `tail`      | `t`   | Tail the current Karaf log file                      |
| `grep`      | `g`   | Grep the latest Karaf log file (requires pattern)    |
| `dir`       | `d`   | Print the latest Karaf directory path                |
| `pushd`     | `p`   | Change to the latest Karaf directory using pushd     |
| `help`      | `h`   | Show help message                                    |

### Examples

```bash
# View log with less
./kt.sh log

# Tail log file
./kt.sh tail

# Search for ERROR in log file
./kt.sh grep ERROR

# Print Karaf directory path
./kt.sh dir

# Change to Karaf directory
./kt.sh pushd
```

### Tips

- The script automatically finds the most recently created Karaf test directory
- All commands have short aliases (single letter) for faster typing
- Error handling is included for missing directories and files
- The script is particularly useful when debugging integration test failures
