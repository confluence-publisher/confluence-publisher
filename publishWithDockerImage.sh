#!/bin/bash

projectVersion=`./mvnw org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -e "^[0-9]\.[0-9]\.[0-9].*" 2>&1`

docker run --network container:confluence-publisher-it --rm \
    -e ROOT_CONFLUENCE_URL=http://localhost:8090 \
    -e USERNAME=confluence-publisher-it \
    -e PASSWORD=1234 \
    -e SPACE_KEY=CPI \
    -e ANCESTOR_ID=327706 \
    -e PAGE_TITLE_PREFIX="Docker - " \
    -e PAGE_TITLE_SUFFIX=" - Test" \
    -e ATTRIBUTES="{ \"confluencePublisherVersion\": \"$projectVersion\"}" \
    -v `pwd`/asciidoc-confluence-publisher-doc/etc/docs:/var/asciidoc-root-folder \
    confluencepublisher/confluence-publisher:${projectVersion}