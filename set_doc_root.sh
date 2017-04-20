#!/bin/bash
DOCROOT=$1
echo "Appending prefix: ${DOCROOT}"
find -name '*.html' | xargs sed -i -e "s%href=\"com%href=\"${DOCROOT}com%g"