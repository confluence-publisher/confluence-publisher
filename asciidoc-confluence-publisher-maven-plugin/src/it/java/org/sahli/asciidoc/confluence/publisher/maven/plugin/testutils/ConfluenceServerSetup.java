package org.sahli.asciidoc.confluence.publisher.maven.plugin.testutils;

import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

public class ConfluenceServerSetup {

    private static final int PORT = 8090;

    public static ConfluenceServer setupContainer() {
        GenericContainer genericContainer = new GenericContainer("confluencepublisher/confluence-publisher-it:6.0.5")
                .withExposedPorts(PORT)
                .withReuse(true)
                .waitingFor(
                        new LogMessageWaitStrategy()
                                .withRegEx(".*(org.apache.catalina.startup.Catalina.start Server startup in).*\n")
                                .withStartupTimeout(Duration.of(120, SECONDS))
                );
        genericContainer.start();
        Integer confluencePort = getConfluencePort(genericContainer);
        return new ConfluenceServer(confluencePort);
    }

    private static Integer getConfluencePort(GenericContainer container) {
        Integer mappedPort = container.getMappedPort(PORT);
        Testcontainers.exposeHostPorts(mappedPort);
        return mappedPort;
    }

}
