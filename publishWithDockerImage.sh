#!/bin/bash

projectVersion=`mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -e "^\d\.\d\.\d.*"`
runnerHost=`hostname`

docker run --rm -e ROOT_CONFLUENCE_URL=http://$runnerHost:8090 \
    -e USERNAME=confluence-publisher-it \
    -e PASSWORD=1234 \
    -e SPACE_KEY=CPI \
    -e ANCESTOR_ID=327706 \
    -e PAGE_TITLE_PREFIX="Docker - " \
    -e PAGE_TITLE_SUFFIX=" - Test" \
    -v `pwd`/asciidoc-confluence-publisher-doc/etc/docs:/var/asciidoc-root-folder \
    confluencepublisher/confluence-publisher:$projectVersion