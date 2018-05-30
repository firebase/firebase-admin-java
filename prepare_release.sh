# Copyright 2018 Google Inc.
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

#!/bin/bash

function parseVersion {
    if [[ ! "$1" =~ ^([0-9]*)\.([0-9]*)\.([0-9]*)$ ]]; then
        return 1
    fi
    return 0
}

set -e

if [ -z "$1" ]; then
    echo "[ERROR] No version number provided."
    echo "[INFO] Usage: ./prepare_release.sh <VERSION_NUMBER>"
    exit 1
fi


#############################
#  VALIDATE VERSION NUMBER  #
#############################

VERSION="$1"
if ! parseVersion "$VERSION"; then
    echo "[ERROR] Illegal version number provided. Version number must match semver."
    exit 1
fi


#############################
#  VALIDATE TEST RESOURCES  #
#############################

if [[ ! -e "integration_cert.json" ]]; then
    echo "[ERROR] integration_cert.json file is required to run integration tests."
    exit 1
fi

if [[ ! -e "integration_apikey.txt" ]]; then
    echo "[ERROR] integration_apikey.txt file is required to run integration tests."
    exit 1
fi


###############
#  INIT REPO  #
###############

# Ensure the current branch does not have local changes
if [[ $(git status --porcelain) ]]; then
    echo "[ERROR] Local changes exist in the current branch. Cannot proceed."
    #exit 1
fi

echo "[INFO] Updating the master branch"
git checkout master
git pull origin master

TIMESTAMP=$(date +%s)
RELEASE_BRANCH="release-${TIMESTAMP}"
echo "[INFO] Creating new release branch: ${RELEASE_BRANCH}"
git checkout -b ${RELEASE_BRANCH} master

HOST=$(uname)
echo "[INFO] Updating CHANGELOG.md"
if [ $HOST == "Darwin" ]; then
    sed -i "" -e "1 s/# Unreleased//" "CHANGELOG.md"
else
    sed -i -e "/# Unreleased/d" "CHANGELOG.md"
fi

echo -e "# Unreleased\n\n-\n\n# v${VERSION}" | cat - CHANGELOG.md > TEMP_CHANGELOG.md
mv TEMP_CHANGELOG.md CHANGELOG.md
git add CHANGELOG.md
git commit -m "Updating CHANGELOG for ${VERSION} release."
git push origin ${RELEASE_BRANCH}


#################################
#  RUN MAVEN PREPARATION STEPS  #
#################################
mvn clean
mvn release:clean
mvn release:prepare -DreleaseVersion=${VERSION} -Dtag=v${VERSION}
