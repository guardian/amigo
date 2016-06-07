# Kong

This role installs [Kong](https://getkong.org/) onto the image.

## gu-configure.sh

The role creates a script `/usr/local/kong/gu-configure.sh` for editing Kong's config file (`/etc/kong/kong.yml`). Your machine should run this script at startup, passing in the IP addresses of all Cassandra hosts.

Note: until you run the script, `/etc/kong/kong.yml` will be invalid (it will contain a couple of placeholders), so Kong will not start.
