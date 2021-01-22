#!/bin/bash
aws ssm --region "$1" get-parameter --name "$2" --with-decryption | jq -r '.Parameter.Value'
