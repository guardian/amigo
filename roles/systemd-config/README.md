# systemd-config

Installs templates to configure systemd.

Only supports _journald.conf_ for now.

# Usage

Select this role in [amigo](https://amigo.gutools.co.uk/recipes) and add, for example:

```
JournaldSystemMaxUse: 50M, JournaldSystemMaxFileSize: 50M
```