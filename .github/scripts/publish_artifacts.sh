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

gpg --quiet --batch --yes --decrypt --passphrase="${MAVEN_SETTINGS_XML}" \
  --output settings.xml .github/resources/settings.xml.gpg

gpg --quiet --batch --yes --decrypt --passphrase="${GPG_PRIVATE_KEY}" \
  --output firebase.asc .github/resources/firebase.asc.gpg

gpg --import firebase.asc

mvn -B clean deploy -Dcheckstyle.skip -DskipTests -Prelease --settings settings.xml

