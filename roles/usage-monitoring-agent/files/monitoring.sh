#!/bin/bash
set -e

GLOBIGNORE="*"

INSTALL_LOCATION="/opt"

function HELP {
>&2 cat << EOF

  Usage: ${0} [-d diskpaths]

  This script installs and schedules continous metrics gathering
  of memory usage, (optionally) disk space usage and (optionally)
  swap usage.

    -d diskpaths  [optional] Comma separated list of mount points to monitor
                  disk space usage for. E.g. /,/var/lib/mongodb

    -h            Displays this help message. No further functions are
                  performed.

EOF
exit 1
}

function install_cloudwatch_client() {
  local __resultvar=$1
  local CLOUDWATCH_CLIENT_VERSION="1.2.1"
  apt-get install -y libwww-perl libdatetime-perl unzip
  wget http://aws-cloudwatch.s3.amazonaws.com/downloads/CloudWatchMonitoringScripts-${CLOUDWATCH_CLIENT_VERSION}.zip
  unzip CloudWatchMonitoringScripts-${CLOUDWATCH_CLIENT_VERSION}.zip
  mv aws-scripts-mon ${INSTALL_LOCATION}
  rm CloudWatchMonitoringScripts-${CLOUDWATCH_CLIENT_VERSION}.zip
}

function generate_cloudwatch_cron_job() {
  local SCRIPT_PATH=$1
  local DISK_PATHS=$2
  local CRON_CMD="${SCRIPT_PATH} --from-cron --auto-scaling=only --mem-util --mem-used --mem-avail --swap-util --swap-used"
  if [ "x${DISK_PATHS}" != "x" ]; then
    CRON_CMD="${CRON_CMD} --disk-space-util --disk-space-used --disk-space-avail"
    for D in $(echo $DISK_PATHS | tr ',' '\n'); do
      CRON_CMD="${CRON_CMD} --disk-path=${D}"
    done
  fi
  echo "${CRON_CMD}"
}

function setup_cron_job() {
  local FREQUENCY_MINUTES=5
  local CRON_CMD=$@
  CRON_ENTRY="*/${FREQUENCY_MINUTES} * * * * ${CRON_CMD}"
  crontab -l > cwlogcron || touch cwlogcron
  echo $CRON_ENTRY >> cwlogcron
  crontab cwlogcron
  rm cwlogcron
}

while getopts s:d:h FLAG; do
  case $FLAG in
    d)
      DISK_PATHS=$OPTARG
      ;;
    h)  #show help
      HELP
      ;;
  esac
done
shift $((OPTIND-1))

install_cloudwatch_client
CLOUDWATCH_SCRIPT="${INSTALL_LOCATION}/aws-scripts-mon/mon-put-instance-data.pl"
CRON_LINE=$(generate_cloudwatch_cron_job $CLOUDWATCH_SCRIPT $DISK_PATHS)
setup_cron_job $CRON_LINE

echo -e "\n\nRoot crontab is now:"
crontab -l
