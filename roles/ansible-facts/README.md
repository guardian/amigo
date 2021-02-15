# Ansible facts

This role facilitates the discovery of Ansible facts (and their values) by printing out the variable 
[`ansible_facts`](https://docs.ansible.com/ansible/latest/user_guide/playbooks_vars_facts.html).

## Example

For the role `aws-cloudwatch-agent` we wanted to install the CloudWatch agent package. The url to download the package 
is dependent on the architecture and operating system e.g. for Ubuntu:
- AMD64: `https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb`
- ARM64: `https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/arm64/latest/amazon-cloudwatch-agent.deb`

The Ansible fact `ansible_architecture` can be used to determine the architecture of the image and therefore which
url link to use. 

We temporarily included this role in the recipes `ubuntu-xenial-capi` and `ubuntu-xenial-capi-ARM` for testing purposes.
After baking the recipes, we were able to determine that the values of `ansible_architecture` were `x86_64` and 
`aarch64` respectively. We then removed the role from the recipes.