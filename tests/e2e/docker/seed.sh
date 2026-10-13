#!/bin/sh
# LocalStack ready hook: seeds the SSM config AMIgo reads in DEV.
set -eu

param() {
  # JSON input stops AWS CLI v1 fetching values that look like URLs.
  awslocal --region eu-west-1 ssm put-parameter --cli-input-json \
    "{\"Name\":\"/DEV/deploy/amigo/$1\",\"Value\":\"$2\",\"Type\":\"String\",\"Overwrite\":true}" >/dev/null
}

param play.http.secret.key "e2e-only-secret-key-not-used-anywhere-else"
param google.clientId amigo-e2e
param google.clientSecret e2e-unused
param auth.mockOidcIssuerUrl http://localhost:8090/google
param auth.google.departmentGroupId e2e-navigation@guardian.co.uk
param auth.google.dataScientistsGroupId e2e-data@guardian.co.uk
param auth.google.multimediaGroupId e2e-multimedia@guardian.co.uk
param packer.subnetId subnet-00000000
param prism.url http://localhost:8091
