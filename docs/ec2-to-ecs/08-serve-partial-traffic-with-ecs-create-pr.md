Run from the repository root with `ec2-to-ecs-08-serve-partial-traffic-with-ecs`
checked out so its committed description file is available:

```shell
git push "origin" "refs/heads/ec2-to-ecs-07-validate-and-tune-ecs:refs/heads/ec2-to-ecs-07-validate-and-tune-ecs" &&
git push "origin" "refs/heads/ec2-to-ecs-08-serve-partial-traffic-with-ecs:refs/heads/ec2-to-ecs-08-serve-partial-traffic-with-ecs" &&
gh pr create --repo "guardian/amigo" --base "ec2-to-ecs-07-validate-and-tune-ecs" --head "ec2-to-ecs-08-serve-partial-traffic-with-ecs" --title "Propose 1 percent ECS traffic alongside EC2" --body-file "docs/ec2-to-ecs/08-serve-partial-traffic-with-ecs.md"
```

The push commands publish existing local commits, not create commits or
deploy AWS resources; publishing branches may trigger normal repository CI.
Both branches must exist on GitHub before creating the PR. Never force-push.
If either push is rejected, stop and resolve remote divergence before
continuing to PR creation.

For missing head/base SHA or "No commits between" errors, verify both remote
branches and their commits in `guardian/amigo`; do not change the base to
`main`. The agent has not executed these publishing or PR-creation commands.
