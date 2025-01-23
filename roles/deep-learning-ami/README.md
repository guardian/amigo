# Deep learning AMI role
This role is designed to be used when https://docs.aws.amazon.com/dlami/latest/devguide/overview-base.html is used as 
the base image base AMI. The aim is to mitigate any compatability issues between that AMI and the roles we have which
are expecting to be run on standard ubuntu.

# Fixes
## Removing pip3 jinja2
Ansible only supports jinja2 <3.1, and the version installed on the deep learning AMI with pip3 is 3.1.5. Jinja2 should
still be available, as it is installed via apt in the python3-jinja2 package.
