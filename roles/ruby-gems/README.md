# ruby-gems

Installs ruby gems with `gem`. The `gems` parameter specifies which gems(s) are installed, e.g.

```
gems: [gem-1, gem-2]
```

If specific versions of gems are needed, the `version_gems` parameter can be used alongside
or instead of the `gems` parameter.

```
version_gems:
  - name: gem-1,
    version: 1.2.3
  - name: gem-2
    version: 5.2.3
```
