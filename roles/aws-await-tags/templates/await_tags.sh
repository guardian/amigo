#!/bin/bash -e
echo "Awaiting AWS tags..."

INSTANCE_ID=$(curl --silent http://169.254.169.254/latest/meta-data/instance-id)

for i in {1..12}
do
    NUM_TAGS=$(aws ec2 describe-tags --filters Name=resource-type,Values=instance Name=resource-id,Values=${INSTANCE_ID} --region eu-west-1 | jq ".Tags | length")

    if (( $NUM_TAGS > 0 )); then
        echo "AWS tags loaded"
        exit 0
    fi

    sleep 5
done

echo "!! AWS Tags not found after 1 minute !!"