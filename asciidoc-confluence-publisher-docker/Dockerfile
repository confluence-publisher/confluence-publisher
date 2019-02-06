FROM openjdk:8-jre-alpine

ADD target/asciidoc-confluence-publisher-docker-*-jar-with-dependencies.jar /opt/asciidoc-confluence-publisher-docker.jar

RUN apk add --update graphviz ttf-dejavu

VOLUME /var/asciidoc-root-folder

ENV SOURCE_ENCODING="" \
    ROOT_CONFLUENCE_URL=""  \
    SPACE_KEY=""  \
    ANCESTOR_ID=""  \
    USERNAME=""  \
    PASSWORD=""  \
    PAGE_TITLE_PREFIX=""  \
    PAGE_TITLE_SUFFIX="" \
    STRATEGY=""

ENTRYPOINT ["sh", "-c", "java -jar /opt/asciidoc-confluence-publisher-docker.jar \
    \"asciidocRootFolder=/var/asciidoc-root-folder\" \
    \"sourceEncoding=$SOURCE_ENCODING\" \
    \"rootConfluenceUrl=$ROOT_CONFLUENCE_URL\" \
    \"spaceKey=$SPACE_KEY\" \
    \"ancestorId=$ANCESTOR_ID\" \
    \"username=$USERNAME\" \
    \"password=$PASSWORD\" \
    \"pageTitlePrefix=$PAGE_TITLE_PREFIX\" \
    \"pageTitleSuffix=$PAGE_TITLE_SUFFIX\" \
    \"strategy=$STRATEGY\" \
"]
