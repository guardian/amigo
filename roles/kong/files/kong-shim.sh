#! /bin/bash
#
# Shim for the kong command that adds the --nginx-conf argument.
# This script's parent directory should be placed on the PATH
# earlier than /usr/local/bin, where the real kong command resides.

case "$@" in
  *start*)
    # either start or restart
    echo Running: /usr/local/bin/kong $@ --nginx-conf /usr/local/kong/nginx.template
    /usr/local/bin/kong $@ --nginx-conf /usr/local/kong/nginx.template
    ;;
  *reload*)
    echo Running: /usr/local/bin/kong $@ --nginx-conf /usr/local/kong/nginx.template
    /usr/local/bin/kong $@ --nginx-conf /usr/local/kong/nginx.template
    ;;
  *)
    /usr/local/bin/kong $@
    ;;
esac


