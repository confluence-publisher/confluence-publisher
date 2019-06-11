FROM openjdk:8-jre-alpine

ADD target/asciidoc-confluence-publisher-docker-*-jar-with-dependencies.jar /opt/asciidoc-confluence-publisher-docker.jar
ADD ./asciidoc-confluence-publisher.sh /opt/asciidoc-confluence-publisher.sh

RUN apk add --update graphviz ttf-dejavu

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
    VERSION_MESSAGE="" \
    ATTRIBUTES=""

ENTRYPOINT ["sh", "-c", "/opt/asciidoc-confluence-publisher.sh"]
