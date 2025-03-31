# Deep learning AMI role
This role is designed to be used when https://docs.aws.amazon.com/dlami/latest/devguide/overview-base.html is used as 
the base image base AMI. The aim is to mitigate any compatability issues between that AMI and the roles we have which
are expecting to be run on standard ubuntu.

# Fixes
## Removing unattended-upgrades
The DLAMI has unattended-upgrades enabled by default, which can cause issues during the bake, and during cloud-init.
We don't need unaattended-upgrades because we always run on a freshly baked AMI!
