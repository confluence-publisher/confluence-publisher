# Contributing

Feedback and contributions to the Confluence Publisher are highly appreciated! When contributing to this repository, 
please ensure that the following points are fulfilled:

- create an issue on GitHub (if not yet existing)
- implement your changes following the style of the existing code
- cover your changes with appropriate automated tests
- create a pull request with a brief description of the relevant changes and design decisions

As our integration test suite requires secret variables only available on branches belonging to the Confluence Publisher 
repository, pull requests will first be merged onto a feature branch within the Confluence Publisher repository. Once 
all integration tests have passed, the feature branch will be merged to the master. In the process, the authorship of 
your changes will _not_ be changed.


## Prerequisites

The following tools need to be installed for building the Confluence Publisher:

- JDK 8 
- Graphviz


## Publishing Documentation
In order to publish the documentation of the Confluence Publisher (which also acts as smoke tests), you need to have a 
Confluence 6.0.5 instance running. You may use the official Docker containers provided by Atlassian (see 
[atlassian/confluence-server 6.0.5](https://hub.docker.com/r/atlassian/confluence-server/tags/)).

Once the Confluence instance is running, perform the following steps:

1. execute `./mvnw clean install` to install the latest version of the Confluence Publisher Maven plugin into your local
  Maven repository
1. overwrite the configuration settings (confluence root, space key, ancestor id, ...) in 
  `asciidoc-confluence-publisher-doc/pom.xml` according to your local Confluence instance
1. publish the documentation using 
  `./mvnw org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-maven-plugin:publish -f asciidoc-confluence-publisher-doc/pom.xml`
 
 Thank you for your contribution!
 The Confluence Publisher Team