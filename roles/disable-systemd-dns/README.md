# Disable SystemD DNS

This Role disables the systemd DNS functionality that was added in Ubuntu bionic. This has been known to cause problems 
 with docker containers accessing the internet. 
 
 - See [here](https://github.com/guardian/amigo/pull/568) for the original amigo PR discussing this after it affected 
 teamcity agents
 - This is where this  'fix' was copied from: https://github.com/moby/libnetwork/issues/2187
