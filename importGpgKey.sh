#!/bin/sh

openssl aes-256-cbc -K $CODE_SIGNING_KEY -iv $CODE_SIGNING_IV -in code-signing-key.asc.enc -out code-signing-key.asc -d
gpg --fast-import --passphrase $CODE_SIGNING_PASSWORD --batch code-signing-key.asc
