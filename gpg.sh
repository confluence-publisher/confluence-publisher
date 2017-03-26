#!/bin/sh

openssl aes-256-cbc -K $encrypted_4c310101c987_key -iv $encrypted_4c310101c987_iv -in code-signing-key.asc.enc -out code-signing-key.asc -d
gpg --fast-import code-signing-key.asc
