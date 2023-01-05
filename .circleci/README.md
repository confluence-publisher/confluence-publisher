# Build

## Renew Code Signing Key

- install `gpg` and `openssl`
- generate new key pair using `gpg --gen-key` (specifying strong private key password)
- export private key using `gpg --armor --export-secret-keys <key-id> > code-signing-key.asc`
- export public key using `gpg --armor --export <key-id> >> code-signing-key.asc`
- generate and memorize initialization vector for file encryption using `openssl rand -hex 16`
- generate and memorize encryption key using `openssl rand -hex 32`
- encrypt exported key pair using `openssl aes-256-cbc -K <encryption key> -iv <initialization vector> -in code-signing-key.asc -out code-signing-key.asc.enc -e`
- configure initialization vector as `CODE_SIGNING_IV` secret variable in build server
- configure encryption key as `CODE_SIGNING_KEY` secret variable in build server
- configure private key password as `CODE_SIGNING_PASSWORD` secret variable in build server
- configure key id as `CODE_SIGNING_ID` secret variable in build server
- commit encrypted key pair `code-signing-key.asc.enc` (do **not** commit exported key pair in plain text)
- publish public key to key server `gpg --send-keys --keyserver hkp://pgp.mit.edu <key-id>`


## Secret Environment Variables

The following secret environment variables have to be set on the build server:

| Name                   | Description                                                | Value                                           |
|------------------------|------------------------------------------------------------| ------------------------------------------------|
| CODE_SIGNING_ID        | id of key pair (see gpg)                                   | FD2EA172 (changes for each new key pair)        |	
| CODE_SIGNING_IV        | initialization vector for key pair encryption              | (defined at key pair generation)                |
| CODE_SIGNING_KEY       | encryption key for key pair encryption                     | (defined at key pair generation)                |	
| CODE_SIGNING_PASSWORD  | private key password                                       | (defined at key pair generation)                |	
| CONFLUENCE_ANCESTOR_ID | ancestor id of documentation in atlassian confluence       | 327681                                          |	
| CONFLUENCE_PASSWORD    | password of atlassian user for publishing documentation    | (secret)                                        |	
| CONFLUENCE_SPACE_KEY   | space key of documentation in atlassian confluence         | CPD                                             |
| CONFLUENCE_URL         | url of documentation in atlassian confluence               | https://confluence-publisher.atlassian.net/wiki |	
| CONFLUENCE_USERNAME    | username of atlassian user for publishing documentation    | confluence-publisher                            |
| DOCKER_HUB_PASSWORD    | password of docker hub user for publishing docker image    | (secret)                                        |	
| DOCKER_HUB_USERNAME    | username of docker hub user for publishing docker image    | confluencepublisher                             |	
| OSSRH_PASSWORD         | password of sonatype user for publishing maven artifacts   | (secret)                                        |	
| OSSRH_USERNAME         | username of sonatype user for publishing maven artifacts   | confluence-publisher                            |
