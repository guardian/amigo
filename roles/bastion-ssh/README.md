# Bastion SSH

Additional SSH configuration for use when running as a Bastion host bridging private subnets
with another network.

N.B. It's recommended that the ubuntu user is additionally removed from sudoers.  This is not included in the role itself because it may interfere with other roles.

```
rm /etc/sudoers.d/90-cloud-init-users
```

It's also recommended to use this role in conjunction with the `cloud-watch-logs` role pointing
at `/var/log/auth`.
