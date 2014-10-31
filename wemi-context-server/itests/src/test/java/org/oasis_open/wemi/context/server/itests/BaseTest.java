package org.oasis_open.wemi.context.server.itests;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;

import java.io.File;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

/**
 * Created by kevan on 31/10/14.
 */
public class BaseTest {
    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.karaf")
                .artifactId("apache-karaf")
                .version("3.0.2")
                .type("tar.gz");

        MavenUrlReference karafStandardRepo = maven()
                .groupId("org.apache.karaf.features")
                .artifactId("standard")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference karafPaxWebRepo = maven()
                .groupId("org.ops4j.pax.web")
                .artifactId("pax-web-features")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference karafSpringRepo = maven()
                .groupId("org.apache.karaf.features")
                .artifactId("spring")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference karafCxfRepo = maven()
                .groupId("org.apache.cxf.karaf")
                .artifactId("apache-cxf")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference karafEnterpriseRepo = maven()
                .groupId("org.apache.karaf.features")
                .artifactId("enterprise")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference wemiServerRepo = maven()
                .groupId("org.oasis-open.wemi")
                .artifactId("wemi-context-server-kar")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        return new Option[]{
                KarafDistributionOption.debugConfiguration("5005", false),
                karafDistributionConfiguration()
                        .frameworkUrl(karafUrl)
                        .unpackDirectory(new File("target/exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(),
                KarafDistributionOption.features(karafPaxWebRepo, "war"),
                KarafDistributionOption.features(karafCxfRepo, "cxf"),
                KarafDistributionOption.features(karafStandardRepo, "openwebbeans"),
                KarafDistributionOption.features(karafStandardRepo, "pax-cdi-web-openwebbeans"),
                KarafDistributionOption.features(wemiServerRepo, "wemi-context-server-kar"),
                // we need to wrap the HttpComponents libraries ourselves since the OSGi bundles provided by the project are incorrect
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpcore").versionAsInProject()),
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpmime").versionAsInProject()),
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpclient").versionAsInProject())
        };
    }
}
