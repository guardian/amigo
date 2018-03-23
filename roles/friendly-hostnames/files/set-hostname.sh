#!/usr/bin/env bash
set -e

FRIENDLY_HOSTNAME=`iconv -f utf8 -t ascii//TRANSLIT /opt/features/friendly-hostnames/hostnames.txt | sed s/[^A-Za-z]*//g | awk 'length>4 && length<30' | shuf -n 1`

echo "Chosen hostname=$FRIENDLY_HOSTNAME"

echo "127.0.1.1 $FRIENDLY_HOSTNAME" >> /etc/hosts
echo $FRIENDLY_HOSTNAME > /etc/hostname
hostname $FRIENDLY_HOSTNAME
rm /opt/features/friendly-hostnames/hostnames.txt

INSTANCE_ID="`wget -qO- http://instance-data/latest/meta-data/instance-id`"
REGION="`wget -qO- http://instance-data/latest/meta-data/placement/availability-zone | sed -e 's:\([0-9][0-9]*\)[a-z]*\$:\\1:'`"

aws ec2 --region $REGION create-tags --resources $INSTANCE_ID --tags Key=Name,Value=$FRIENDLY_HOSTNAME
