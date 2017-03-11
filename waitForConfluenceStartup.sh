#!/bin/bash

containerName=$1
timeoutInSeconds=$2

timeout ${timeoutInSeconds} grep -q "org.apache.catalina.startup.Catalina.start Server startup in" <(docker logs -f ${containerName} 2>&1)
