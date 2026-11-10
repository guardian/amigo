Run from the repository root with `ec2-to-ecs-07-validate-and-tune-ecs`
checked out so its committed description file is available:

```shell
git push "origin" "refs/heads/ec2-to-ecs-06-configure-and-provision-ecs-tasks:refs/heads/ec2-to-ecs-06-configure-and-provision-ecs-tasks" &&
git push "origin" "refs/heads/ec2-to-ecs-07-validate-and-tune-ecs:refs/heads/ec2-to-ecs-07-validate-and-tune-ecs" &&
gh pr create --repo "guardian/amigo" --base "ec2-to-ecs-06-configure-and-provision-ecs-tasks" --head "ec2-to-ecs-07-validate-and-tune-ecs" --title "Plan CODE ECS load testing and bake validation" --body-file "docs/ec2-to-ecs/07-validate-and-tune-ecs.md"
```

The pushes publish existing local commits; they do not create commits or
deploy AWS resources, but may trigger normal repository CI. Both branches
must be on GitHub before creating the PR. Never force-push. If either push
is rejected, stop and resolve divergence rather than continuing.

For missing head/base SHA or "No commits between" errors, verify both branches
and their commits in `guardian/amigo`; do not change the base to `main`.
The agent has not run these publishing or PR-creation commands.
