# ruby-gems

Installs ruby gems with `gem`. The `gems` parameter specifies which gems(s) are installed, e.g.

```
gems: [gem-1, gem-2]
```

If a specific version of a gem is needed, the `version_gems` parameter can be used alongside
or instead of the `gems` parameter, as shown below. The items in `version_gems` will be split
on the "=" delimiter, with the first element used as the package name, and the second as the
version.

```
version_gems: [ "gem1=1.2.3", "gem2=5.8.3" ]
```
