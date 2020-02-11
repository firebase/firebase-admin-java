#!/bin/bash

# Copyright 2020 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e
set -u

gpg --quiet --batch --yes --decrypt --passphrase="${FIREBASE_SERVICE_ACCT_KEY}" \
  --output integration_cert.json .github/resources/integ-service-account.json.gpg

echo "${FIREBASE_API_KEY}" > integration_apikey.txt

# Does the following:
#  1. Runs the Checkstyle plugin (validate phase)
#  2. Compiles the source (compile phase)
#  3. Runs the unit tests (test phase)
#  4. Packages the artifacts - src, bin, javadocs (package phase)
#  5. Runs the integration tests (verify phase)
mvn -B clean verify

# Maven target directory can consist of many files. Just copy the jar artifacts
# into a new directory for upload.
mkdir -p dist
cp target/*.jar dist/
