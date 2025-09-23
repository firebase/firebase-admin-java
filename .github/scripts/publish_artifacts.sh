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

gpg --quiet --batch --yes --decrypt --passphrase="${GPG_PRIVATE_KEY}" \
  --output firebase.asc .github/resources/firebase.asc.gpg

gpg --import --no-tty --batch --yes firebase.asc

# Does the following:
#  1. Compiles the source (compile phase)
#  2. Packages the artifacts - src, bin, javadocs (package phase)
#  3. Signs the artifacts (verify phase)
#  4. Publishes artifacts via Central Publisher Portal (deploy phase)
mvn -B clean deploy \
  -Dcheckstyle.skip \
  -DskipTests \
  -Prelease \
  --settings .github/resources/settings.xml

