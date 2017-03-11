#!/bin/bash

confluenceBaseUrl=$1

curl -s -u confluence-publisher-it:1234 ${confluenceBaseUrl}/rest/api/content/327706 | grep "Publishing Root"
