# apt

Makes sure that the `apt` cache is up to date and upgrades all installed packages to the latest version.

Any role that involves installing a package using `apt` should depend on this role, 
to ensure that the `apt` package cache is up to date before the package gets installed.