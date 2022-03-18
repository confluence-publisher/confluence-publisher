# Contributing

Feedback and contributions to the Confluence Publisher are highly appreciated! When contributing to this repository, 
please ensure that the following points are fulfilled:

- create an issue on GitHub (if not yet existing)
- implement your changes following the style of the existing code
- cover your changes with appropriate automated tests
- document new features and newly introduced configuration options in documentation (both for Maven- and for 
  Docker-based publishing)
- create a pull request with a brief description of the relevant changes and design decisions and reference the issue 

When doing a pull request, please try to implement the feature / bug fix with minimal design and code formatting 
changes, so that reviewing and integrating the pull request is as easy as possible. 


## Conventions

Please see the following (incomplete) list of guidelines applied to the code base:

**Java**
  - avoid method references, use explicit lambdas instead
  - always put lambda parameters in parentheses
  - make methods static when possible
  - make fields final when possible
  - never make parameters and local variables final (unless required by compiler)
  - avoid randomly breaking long lines (extract utility methods instead, or keep long lines)
  - use triple-a approach (arrange, act, assert) for unit testing (see existing tests)
   
**Maven**
  - manage dependency versions in top-level pom with explicit scope set (normally either runtime or test)
  - order dependencies: own dependencies first, followed by third-party dependencies sorted by group-id, then 
    artifact-id (not by scope first)
  - prefer JDK functionality over third-party dependencies
  - judge wisely whether adding a new third-party dependency is worth just for using that one utility method


## Prerequisites

The following tools need to be installed for building the Confluence Publisher:

- JDK 11 
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