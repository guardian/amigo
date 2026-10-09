#!/bin/sh
# Seeds the moto container with the SSM config AMIgo reads in DEV.
set -eu

param() {
  aws ssm put-parameter --overwrite --type String \
    --name "/DEV/deploy/amigo/$1" --value "$2" >/dev/null
}

param play.http.secret.key "e2e-only-secret-key-not-used-anywhere-else"
param google.clientId amigo-e2e
param google.clientSecret e2e-unused
param auth.mockOidcDiscoveryUrl http://localhost:8090/google/.well-known/openid-configuration
param auth.google.departmentGroupId e2e-navigation@guardian.co.uk
param auth.google.dataScientistsGroupId e2e-data@guardian.co.uk
param auth.google.multimediaGroupId e2e-multimedia@guardian.co.uk
param packer.subnetId subnet-00000000
param prism.url http://localhost:8091
