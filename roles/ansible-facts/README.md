# Ansible facts

This role facilitates the discovery of Ansible facts and magic variables by printing out the variable 
[`ansible_facts`](https://docs.ansible.com/ansible/latest/user_guide/playbooks_vars_facts.html) e.g. (for a given
base image) getting the values for `ansible_os_family` and `ansible_architecture`. 