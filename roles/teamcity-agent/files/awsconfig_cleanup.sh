#!/bin/bash -e

if [ -d "/opt/teamcity/.aws" ]; then
    ls -lhd /opt/teamcity/.aws
    echo Existing .aws config directory present in /opt/teamcity, removing...
    rm -rf /opt/teamcity/.aws
    echo All gone!
else
    echo No existing .aws config directory present
fi