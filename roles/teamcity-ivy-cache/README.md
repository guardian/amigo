# Teamcity IVY Cache

This sets up the ivy cache on a teamcity agent and synchronises it.

It gets reported as unused in "Used by" as it's not directly used by a recipe.
It is however used by the [`teamcity-agent`](../teamcity-agent/tasks/main.yml) role via `import_role`.
