#!/bin/bash

projectVersion=`./mvnw org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -e "^[0-9]\+\.[0-9]\+\.[0-9]\+.*" 2>&1`

docker run --network container:confluence-publisher-it --rm \
    -e ROOT_CONFLUENCE_URL=$CPI_ROOT_URL \
    -e USERNAME=$CPI_USERNAME \
    -e PASSWORD="$CPI_PASSWORD" \
    -e SPACE_KEY=$CPI_SPACE_KEY \
    -e ANCESTOR_ID=$CPI_ANCESTOR_ID \
    -e REST_API_VERSION=$CPI_REST_API_VERSION \
    -e PAGE_TITLE_PREFIX="Docker - " \
    -e PAGE_TITLE_SUFFIX=" - Test" \
    -e ATTRIBUTES="{ \"confluencePublisherVersion\": \"$projectVersion\"}" \
    -v `pwd`/asciidoc-confluence-publisher-doc/etc/docs:/var/asciidoc-root-folder \
    confluencepublisher/confluence-publisher:${projectVersion}
