#!/bin/bash
#
# Configuration script for the Kong feature.
#
# Given a Postgres hostname, username and password rewrite kong.conf to use this host (on port 5432) with the given username and password.
#
# Also sets Kong's cluster advertise address to the instance's internal IPv4 address
#
# This script must be run as root
set -e

USAGE="Usage: $0 postgres_host postgres_username postgres_password_file kong_user

Example: $0 127.0.0.1 kong_username /dir/to/my/password content-api
"

search_internal_ip='INTERNAL_IP'
replace_internal_ip=$(ec2metadata --local-ipv4)

search_postgres_host='POSTGRES_HOST'
replace_postgres_host=${1?postgres_host_missing}

search_postgres_username='POSTGRES_USERNAME'
replace_postgres_username=${2?postgres_username_missing}

search_postgres_password='POSTGRES_PASSWORD'
replace_postgres_password=$(cat ${3?postgres_password_file_missing})

kong_user=${4?kong_user_missing}

echo "Backing up kong.conf to /etc/kong.conf.bak"
cp /etc/kong.conf{,.bak}

echo "Writing Postgres config to kong.conf"
# Use perl because sed doesn't play well with newlines
perl -pe "s/${search_internal_ip}/${replace_internal_ip}/;" \
     -pe "s/${search_postgres_host}/${replace_postgres_host}/;" \
     -pe "s/${search_postgres_username}/${replace_postgres_username}/;" \
     -pe "s/${search_postgres_password}/${replace_postgres_password}/;" /etc/kong.conf.bak > /etc/kong.conf


chmod 400 /etc/kong.conf
chown $kong_user /etc/kong.conf
