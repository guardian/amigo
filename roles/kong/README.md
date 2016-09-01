# Kong

This role installs [Kong](https://getkong.org/) onto the image.

## gu-configure.sh

The role creates a script `/usr/local/kong/gu-configure.sh` for editing Kong's config file (`/etc/kong/kong.conf`). Your machine should run this script at startup, passing in the DNS name of the postgres host.

Note: until you run the script, `/etc/kong/kong.conf` will be invalid (it will contain a couple of placeholders), so Kong will not start.
