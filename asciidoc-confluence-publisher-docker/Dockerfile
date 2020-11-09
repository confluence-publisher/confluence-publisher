FROM openjdk:8-jre-alpine

RUN apk add --update graphviz ttf-dejavu

ADD target/asciidoc-confluence-publisher-docker-*-jar-with-dependencies.jar /opt/asciidoc-confluence-publisher-docker.jar
ADD ./publish.sh /usr/local/bin

VOLUME /var/asciidoc-root-folder

ENV ASCIIDOC_ROOT_FOLDER="/var/asciidoc-root-folder" \
    SOURCE_ENCODING="" \
    ROOT_CONFLUENCE_URL=""  \
    SKIP_SSL_VERIFICATION="false" \
    SPACE_KEY=""  \
    ANCESTOR_ID=""  \
    USERNAME=""  \
    PASSWORD=""  \
    PAGE_TITLE_PREFIX=""  \
    PAGE_TITLE_SUFFIX="" \
    PUBLISHING_STRATEGY="" \
    ORPHAN_REMOVAL_STRATEGY="" \
    VERSION_MESSAGE="" \
    NOTIFY_WATCHERS="true" \
    ATTRIBUTES="" \
    PROXY_SCHEME="" \
    PROXY_HOST="" \
    PROXY_PORT="" \
    PROXY_USERNAME="" \
    PROXY_PASSWORD="" \
    CONVERT_ONLY="false"

ENTRYPOINT ["publish.sh"]
