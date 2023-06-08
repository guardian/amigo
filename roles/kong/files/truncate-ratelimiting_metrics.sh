#! /bin/bash
#
# Removes old entries from Kong's ratelimiting_metrics table
# to prevent the table from growing indefinitely.
# Intended to run periodically throughout the day as opposed to once. This avoids deleting a really large number
# of rows from Postgres all at once and causing issues with Postgres.
#
# Looks up the DB host and password from /etc/kong.conf.
#
# This script must be run as a user that can read /etc/kong.conf
# i.e. the kong user or root.

host=$(grep -o -P '(?<=pg_host = )(.*?)(?= )' /etc/kong.conf)
password=$(grep -o -P '(?<=pg_password = )(.*?)(?= )' /etc/kong.conf)

PGPASSWORD=$password psql -U kong -h $host -c "delete from ratelimiting_metrics where period_date < now() - '24 hours'::interval"


