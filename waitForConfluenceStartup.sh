#!/bin/bash

containerName=$1

while [ ! "$(docker logs ${containerName} | grep "org.apache.catalina.startup.Catalina.start Server startup in")" ]; do
    sleep 1
done
