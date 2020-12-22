# pip-packages

Install Python 2 package(s) with `pip`. The `packages` parameter specifies which
package(s) are installed, e.g.

```
packages: [flask, ocrmypdf]
```

**NB**: this role depends on the `pip` role, which in turn depends on the `python-pip`
package. This is no longer available after Ubuntu 20.04 (Focal), where Python 3 becomes
the only version available. Python 2 is EOL so please use `pip3-packages` where
possible.