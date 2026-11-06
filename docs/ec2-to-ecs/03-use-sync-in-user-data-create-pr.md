Run this command from the repository root with
`ec2-to-ecs-03-use-sync-in-user-data` checked out, after pushing the base and
step branches:

```shell
gh pr create --base "ec2-to-ecs-02-upversion-cdk" --head "ec2-to-ecs-03-use-sync-in-user-data" --title "Sync AMIgo configuration into a single directory" --body-file "docs/ec2-to-ecs/03-use-sync-in-user-data.md"
```
