#!/bin/sh

# Pause the syslog daemon
# move /var/log to the target redirect path
# Restart syslog

REDIRECT_PATH=$1

if [ -d "/encrypted" ]; then
    sudo /etc/init.d/rsyslog stop
    mv /var/log $REDIRECT_PATH
    sudo ln -s "$REDIRECT_PATH/log" /var/log
    sudo /etc/init.d/rsyslog start
fi
