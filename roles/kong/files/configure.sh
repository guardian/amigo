#!/bin/bash
#
# Configuration script for the Kong feature.
#
# Given a Postgres hostname, rewrites kong.conf to use this host (on port 5432)
#
# Also sets Kong's cluster advertise address to the instance's internal IPv4 address
#
# This script must be run as root
set -e

USAGE="Usage: $0 postgres_host postgres_username postgres_password

Example: $0 1.2.3.4 kong mysecretpassword
"
test -z $1 && echo 'Postgres host missing.' && exit 1
test -z $2 && echo 'Postgres username missing' && exit 1
test -z $3 && echo 'Postgres password missing.' && exit 1


search_internal_ip='INTERNAL_IP'
replace_internal_ip=$(ec2metadata --local-ipv4)

search_postgres_host='POSTGRES_HOST'
replace_postgres_host=$1

search_postgres_username='POSTGRES_USERNAME'
replace_postgres_username=$2

search_postgres_password='POSTGRES_PASSWORD'
replace_postgres_password=$3

echo "Backing up kong.conf to /etc/kong/kong.conf.bak"
cp /etc/kong/kong.conf{,.bak}

echo "Writing Postgres config to kong.conf"
# Use perl because sed doesn't play well with newlines
perl -pe "s/${search_internal_ip}/${replace_internal_ip}/;" \
     -pe "s/${search_postgres_host}/${replace_postgres_host}/;" \
     -pe "s/${search_postgres_username}/${replace_postgres_username}/;" \
     -pe "s/${search_postgres_password}/${replace_postgres_password}/;" /etc/kong/kong.conf.bak > /etc/kong/kong.conf
