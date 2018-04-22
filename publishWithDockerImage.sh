#!/bin/bash

projectVersion=`./mvnw org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -e "^[0-9]\.[0-9]\.[0-9].*" 2>&1`
runnerHostIp=`ifconfig eth0 | sed -En 's/127.0.0.1//;s/.*inet (addr:)?(([0-9]*\.){3}[0-9]*).*/\2/p'`

docker run --rm \
    -e ROOT_CONFLUENCE_URL=http://${runnerHostIp}:8090 \
    -e USERNAME=confluence-publisher-it \
    -e PASSWORD=1234 \
    -e SPACE_KEY=CPI \
    -e ANCESTOR_ID=327706 \
    -e PAGE_TITLE_PREFIX="Docker - " \
    -e PAGE_TITLE_SUFFIX=" - Test" \
    -v `pwd`/asciidoc-confluence-publisher-doc/etc/docs:/var/asciidoc-root-folder \
    confluencepublisher/confluence-publisher:${projectVersion}