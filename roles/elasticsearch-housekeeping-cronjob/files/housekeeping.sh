#!/bin/bash

# Perform ElasticSearch housekeeping.
# We cut the first 2 columns from the output and pipe it into syslog

# Delete old kibana index snapshots
/usr/local/bin/curator --master-only delete snapshots --repository s3_backup --timestring '%Y%m%d%H%M' --time-unit days --older-than 14 2>&1 | cut -d " " -f 3- | logger -t curator

# Cleanup indexes
/usr/local/bin/curator --master-only delete indices --timestring '%Y.%m.%d' --time-unit days --older-than 14 2>&1 | cut -d " " -f 3- | logger -t curator

# Optimise older indexes
/usr/local/bin/curator --master-only optimize indices --timestring '%Y.%m.%d' --time-unit days --older-than 1 2>&1 | cut -d " " -f 3- | logger -t curator