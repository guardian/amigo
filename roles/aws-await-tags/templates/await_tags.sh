#!/bin/bash -e
echo "Awaiting AWS tags..."

INSTANCE_ID=$(curl --silent http://169.254.169.254/latest/meta-data/instance-id)

for i in {1..12}
do
    NUM_TAGS=$(/usr/local/bin/aws ec2 describe-tags --filters Name=resource-type,Values=instance Name=resource-id,Values=${INSTANCE_ID} --region eu-west-1 | jq ".Tags | length")

    if [[ -z "$NUM_TAGS" ]]; then
        echo "Unable to call describe tags. Exiting immediately"
        exit 255
    fi

    if (( $NUM_TAGS > 0 )); then
        echo "AWS tags loaded"
        exit 0
    fi

    sleep 5
done

# Ideally we should exit 1 here but cloud-init seems to ignore the failure and press on regardless...
# so lets just complain in the syslog and hope someone is watching
echo "AWS Tags not found after 1 minute"
