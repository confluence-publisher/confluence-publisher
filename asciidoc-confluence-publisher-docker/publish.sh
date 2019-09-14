#!/usr/bin/env sh
exec java -jar /opt/asciidoc-confluence-publisher-docker.jar \
    asciidocRootFolder="$ASCIIDOC_ROOT_FOLDER" \
    sourceEncoding="$SOURCE_ENCODING" \
    rootConfluenceUrl="$ROOT_CONFLUENCE_URL" \
    skipSslVerification="$SKIP_SSL_VERIFICATION" \
    spaceKey="$SPACE_KEY" \
    ancestorId="$ANCESTOR_ID" \
    username="$USERNAME" \
    password="$PASSWORD" \
    pageTitlePrefix="$PAGE_TITLE_PREFIX" \
    pageTitleSuffix="$PAGE_TITLE_SUFFIX" \
    publishingStrategy="$PUBLISHING_STRATEGY" \
    versionMessage="$VERSION_MESSAGE" \
    attributes="$ATTRIBUTES" \
    proxyScheme="$PROXY_SCHEME" \
    proxyHost="$PROXY_HOST" \
    proxyPort="$PROXY_PORT" \
    proxyUsername="$PROXY_USERNAME" \
    proxyPassword="$PROXY_PASSWORD"
