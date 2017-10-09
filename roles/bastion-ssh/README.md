# Bastion SSH

Additional SSH configuration for use when running as a Bastion host bridging private subnets
with another network.

This role depends on the s3-ssh-keys role which will keep the list of SSH keys on the Bastion
up to date. Due to the way parameters are passed to Ansible, you should add the parameters to
this role, not the s3-ssh-keys one:

```
bastion-ssh { ssh_keys_bucket: github-public-keys, ssh_keys_prefix: <team> }
```

It's recommended to use this role in conjunction with the `cloud-watch-logs` role pointing
at `/var/log/auth`.