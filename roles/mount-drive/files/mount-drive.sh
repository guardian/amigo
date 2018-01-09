#!/bin/sh

# Mount a drive
DEVICE=$1
MOUNT_PATH=$2

if [ -e "$DEVICE" ]; then
    mkdir "$MOUNT_PATH"
    mkfs -t ext4 "$DEVICE"
    echo "$DEVICE" "$MOUNT_PATH" ext4 defaults 0 0" >> /etc/fstab
    mount -a
else
    echo "Device $DEVICE does not exist" 1>&2
fi
