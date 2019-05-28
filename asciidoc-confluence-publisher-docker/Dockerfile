FROM openjdk:8-jre-alpine

ADD target/asciidoc-confluence-publisher-docker-*-jar-with-dependencies.jar /opt/asciidoc-confluence-publisher-docker.jar

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

ENTRYPOINT ["sh", "-c", "java -jar /opt/asciidoc-confluence-publisher-docker.jar \
    \"asciidocRootFolder=$ASCIIDOC_ROOT_FOLDER\" \
    \"sourceEncoding=$SOURCE_ENCODING\" \
    \"rootConfluenceUrl=$ROOT_CONFLUENCE_URL\" \
    \"skipSslVerification=$SKIP_SSL_VERIFICATION\" \
    \"spaceKey=$SPACE_KEY\" \
    \"ancestorId=$ANCESTOR_ID\" \
    \"username=$USERNAME\" \
    \"password=$PASSWORD\" \
    \"pageTitlePrefix=$PAGE_TITLE_PREFIX\" \
    \"pageTitleSuffix=$PAGE_TITLE_SUFFIX\" \
    \"publishingStrategy=$PUBLISHING_STRATEGY\" \
    \"versionMessage=$VERSION_MESSAGE\" \
    \"attributes=$ATTRIBUTES\" \
"]
