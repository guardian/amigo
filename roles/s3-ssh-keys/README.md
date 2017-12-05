S3 SSH keys
===========

Copies an authorized_keys file to the .ssh directory of the `ssh_user`
(defaults to ubuntu) from the S3 bucket specified with
`ssh_keys_bucket` and a directory of `ssh_keys_prefix`.

For example specifying

    ssh_keys_bucket: my-bucket-name, ssh_keys_prefix: Team-Name


would result in the file at
`s3://my-bucket-name/Team-Name/authorized_keys` being copied to
`.ssh/authorized_keys` within the `ubuntu` users home directory.

You can pass a list of team names for the ssh_keys_prefix value to
compose multiple files. E.g:

    ssh_keys_bucket: my-bucket-name, ssh_keys_prefix: [Team-NameA, Team-NameB]

In addition to downloading the keys during AMI creation, this creates
a systemd timer to keep the keys up to date. It will re-download the
file a minute after startup and every half hour thereafter.

To see when the last time the job was run and when it is next
scheduled to run, you can use `systemctl list-timers`. Logs for the
job can be inspected using `journalctl -u update-keys`.
