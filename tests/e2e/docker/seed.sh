#!/bin/sh
# Seeds the moto container with the SSM config and DynamoDB data AMIgo reads in DEV.
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

table() {
  name="amigo-DEV-$1"
  shift
  aws dynamodb describe-table --table-name "$name" >/dev/null 2>&1 ||
    aws dynamodb create-table --table-name "$name" \
      --provisioned-throughput ReadCapacityUnits=1,WriteCapacityUnits=1 "$@" >/dev/null
}

table base-images \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH
table recipes \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH
table bakes \
  --attribute-definitions AttributeName=recipeId,AttributeType=S AttributeName=buildNumber,AttributeType=N \
  --key-schema AttributeName=recipeId,KeyType=HASH AttributeName=buildNumber,KeyType=RANGE
table bake-logs \
  --attribute-definitions AttributeName=bakeId,AttributeType=S AttributeName=logNumber,AttributeType=N \
  --key-schema AttributeName=bakeId,KeyType=HASH AttributeName=logNumber,KeyType=RANGE

aws dynamodb put-item --table-name amigo-DEV-base-images --item '{
  "id": {"S": "e2e-base"},
  "description": {"S": "Synthetic base image for navigation tests"},
  "amiId": {"S": "ami-00000000000000000"},
  "builtinRoles": {"L": []},
  "createdBy": {"S": "E2E"},
  "createdAt": {"S": "2026-01-01T00:00:00.000Z"},
  "modifiedBy": {"S": "E2E"},
  "modifiedAt": {"S": "2026-01-01T00:00:00.000Z"},
  "requiresXLargeBuilder": {"BOOL": false}
}'

aws dynamodb put-item --table-name amigo-DEV-recipes --item '{
  "id": {"S": "e2e-recipe"},
  "description": {"S": "Synthetic recipe for navigation tests"},
  "baseImageId": {"S": "e2e-base"},
  "roles": {"L": []},
  "nextBuildNumber": {"N": "0"},
  "createdBy": {"S": "E2E"},
  "createdAt": {"S": "2026-01-01T00:00:00.000Z"},
  "modifiedBy": {"S": "E2E"},
  "modifiedAt": {"S": "2026-01-01T00:00:00.000Z"},
  "encryptFor": {"L": []}
}'
