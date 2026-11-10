Run from the repository root with `ec2-to-ecs-09-cut-over-to-ecs` checked out
so its committed description file is available:

```shell
git push "origin" "refs/heads/ec2-to-ecs-08-serve-partial-traffic-with-ecs:refs/heads/ec2-to-ecs-08-serve-partial-traffic-with-ecs" &&
git push "origin" "refs/heads/ec2-to-ecs-09-cut-over-to-ecs:refs/heads/ec2-to-ecs-09-cut-over-to-ecs" &&
gh pr create --repo "guardian/amigo" --base "ec2-to-ecs-08-serve-partial-traffic-with-ecs" --head "ec2-to-ecs-09-cut-over-to-ecs" --title "Propose full ECS traffic with EC2 retained for rollback" --body-file "docs/ec2-to-ecs/09-cut-over-to-ecs.md"
```

These push commands publish existing local commits, not create commits or
deploy AWS resources; publishing branches may trigger normal repository CI.
Both branches must exist on GitHub before PR creation. Never force-push.
If either push is rejected, stop and resolve divergence with the user rather
than proceeding to PR creation.

For missing head/base SHA or "No commits between" errors, verify both remote
branches and their commits in `guardian/amigo`; do not change the base to
`main`. The agent has not executed these commands.
