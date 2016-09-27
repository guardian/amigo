#! /bin/bash
#
# Removes old entries from Kong's ratelimiting_metrics table
# to prevent the table from growing indefinitely.
#
# Looks up the DB host and password from /etc/kong.conf.
#
# This script must be run as a user that can read /etc/kong.conf
# i.e. the kong user or root.

host=$(grep -o -P '(?<=pg_host = )(.*?)(?= )' /etc/kong.conf)
password=$(grep -o -P '(?<=pg_password = )(.*?)(?= )' /etc/kong.conf)

PGPASSWORD=$password psql -U kong -h $host -c "delete from ratelimiting_metrics where period_date < now() - '24 hours'::interval"
