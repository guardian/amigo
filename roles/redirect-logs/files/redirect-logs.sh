#!/bin/sh

# Pause the syslog daemon
# move /var/log to the target redirect path
# Restart syslog

redirect_path=$1

if [ -d "$redirect_path" ]; then
    sudo /etc/init.d/rsyslog stop
    mv /var/log "$redirect_path"
    sudo ln -s "$redirect_path/log" /var/log
    sudo /etc/init.d/rsyslog start
fi
