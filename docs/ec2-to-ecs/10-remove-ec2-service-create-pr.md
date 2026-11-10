# Publish the Step 10 proposal and create its PR

Run from the repository root with
`ec2-to-ecs-10-remove-ec2-service` checked out so its description file is
available. These commands are for the user; the agent has not run them.

The pushes publish existing local commits; they do not create commits or
deploy AWS resources. Publishing branches may trigger normal repository CI,
including its image/artifact publication. No force-push is used. If either
push is rejected, stop and resolve remote divergence before creating a PR.

```shell
git push "origin" "refs/heads/ec2-to-ecs-09-cut-over-to-ecs:refs/heads/ec2-to-ecs-09-cut-over-to-ecs" &&
git push "origin" "refs/heads/ec2-to-ecs-10-remove-ec2-service:refs/heads/ec2-to-ecs-10-remove-ec2-service" &&
gh pr create --repo "guardian/amigo" --base "ec2-to-ecs-09-cut-over-to-ecs" --head "ec2-to-ecs-10-remove-ec2-service" --title "Remove the AMIgo EC2 application deployment" --body-file "docs/ec2-to-ecs/10-remove-ec2-service.md"
```

For missing head/base SHA or "No commits between" errors, verify both remote
branches and their commits in `guardian/amigo`. Do not change the base to
`main` as a workaround. Read the removal warning and satisfy the merge
prerequisites in the description before merging or deploying.
