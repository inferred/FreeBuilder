#!/usr/bin/env bash
set -e
set -x

openssl aes-256-cbc -K ${encrypted_ecf6a9d6c834_key:?not set} -iv ${encrypted_ecf6a9d6c834_iv:?not set} -in codesigning.asc.enc -out codesigning.asc -d
gpg --fast-import -q codesigning.asc
