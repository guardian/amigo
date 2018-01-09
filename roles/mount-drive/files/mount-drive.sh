#!/bin/sh

# Mount a drive
device=$1
mount_path=$2

if [ -e "$device" ]; then
    mkdir "$mount_path"
    mkfs -t ext4 "$device"
    echo "$device $mount_path ext4 defaults 0 0" >> /etc/fstab
    mount -a
else
    echo "Device $device does not exist" 1>&2
fi
