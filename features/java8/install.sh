#!/usr/bin/env bash

apt-get --yes --force-yes install openjdk-8-jdk openjdk-8-jre-headless

## Workaround for Debian Java packaging bug
## See:
## https://github.com/guardian/status-app/blob/play-2.4/cloud-formation/status-app.json#L159
## https://bugs.launchpad.net/ubuntu/+source/ca-certificates-java/+bug/1396760
## https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=775775
/var/lib/dpkg/info/ca-certificates-java.postinst configure
