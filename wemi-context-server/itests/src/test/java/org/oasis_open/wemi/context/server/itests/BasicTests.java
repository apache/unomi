package org.oasis_open.wemi.context.server.itests;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

import java.io.File;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.ConfigurationPointer;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.configs.FeaturesCfg;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.options.ProvisionOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by loom on 04.08.14.
 */
@RunWith(PaxExam.class)
public class BasicTests {

    private static Logger LOG = LoggerFactory.getLogger(BasicTests.class);

    /*
    @Inject
    protected Calculator calculator;
    */

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
            .groupId("org.apache.karaf")
            .artifactId("apache-karaf")
            .version("3.0.1")
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
        return new Option[] {
            // KarafDistributionOption.debugConfiguration("5005", true),
            karafDistributionConfiguration()
                .frameworkUrl(karafUrl)
                .unpackDirectory(new File("target/exam"))
                .useDeployFolder(false),
            keepRuntimeFolder(),
            KarafDistributionOption.features(karafPaxWebRepo , "war"),
            KarafDistributionOption.features(karafCxfRepo , "cxf"),
            KarafDistributionOption.features(karafStandardRepo , "openwebbeans"),
            KarafDistributionOption.features(karafStandardRepo , "pax-cdi-web-openwebbeans"),
            KarafDistributionOption.features(wemiServerRepo , "wemi-context-server-kar"),
            mavenBundle()
                .groupId("org.oasis-open.wemi")
                .artifactId("wemi-context-server-wab")
                .versionAsInProject().start(),
       };
    }

    @Test
    public void testAdd() {
        LOG.info("testAdd test executed");
        /*
        int result = calculator.add(1, 2);
        LOG.info("Result of add was {}", result);
        Assert.assertEquals(3, result);
        */
    }

}
