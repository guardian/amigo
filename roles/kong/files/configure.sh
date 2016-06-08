#!/bin/bash
#
# Configuration script for the Kong feature.
#
# Given a list of Cassandra hostnames, rewrites kong.yml to use those hosts (on port 9042)
#
# Also sets Kong's cluster advertise address to the instance's internal IPv4 address
#
# This script must be run as root
set -e

USAGE="Usage: $0 [cassandrahost1 [cassandrahost2 ...]]

Example: $0 1.2.3.4 5.6.7.8
"

search_internal_ip='INTERNAL_IP'
replace_internal_ip=$(ec2metadata --local-ipv4)

search_cass_hosts='CASSANDRA_HOSTS'
replace_cass_hosts=''
for host in "$@"; do
  replace_cass_hosts="$replace_cass_hosts    - \"${host}:9042\"\n"
done

echo "Backing up kong.yml to /etc/kong/kong.yml.bak"
cp /etc/kong/kong.yml{,.bak}

echo "Writing Cassandra hostnames to kong.yml"
# Use perl because sed doesn't play well with newlines
perl -pe "s/${search_cass_hosts}/${replace_cass_hosts}/;" -pe "s/${search_internal_ip}/${replace_internal_ip}/;" /etc/kong/kong.yml.bak > /etc/kong/kong.yml
