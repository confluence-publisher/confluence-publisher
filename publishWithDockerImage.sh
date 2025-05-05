#!/bin/bash

projectVersion=`./mvnw org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -e "^[0-9]\+\.[0-9]\+\.[0-9]\+.*" 2>&1`

docker run --network container:confluence-publisher-it --rm \
    -e ROOT_CONFLUENCE_URL=https://confluence-publisher.atlassian.net/wiki \
    -e USERNAME=$CONFLUENCE_IT_USERNAME \
    -e PASSWORD="$CONFLUENCE_IT_TOKEN" \
    -e SPACE_KEY=CPIV1 \
    -e ANCESTOR_ID=3153166339 \
    -e PAGE_TITLE_PREFIX="Docker - " \
    -e PAGE_TITLE_SUFFIX=" - Test" \
    -e ATTRIBUTES="{ \"confluencePublisherVersion\": \"$projectVersion\"}" \
    -v `pwd`/asciidoc-confluence-publisher-doc/etc/docs:/var/asciidoc-root-folder \
    confluencepublisher/confluence-publisher:${projectVersion}
