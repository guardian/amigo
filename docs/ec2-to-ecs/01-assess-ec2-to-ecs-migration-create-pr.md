Run this command from the repository root with
`ec2-to-ecs-01-assess-ec2-to-ecs-migration` checked out, after pushing both
the base and step branches:

```shell
gh pr create --base "ec2-to-ecs-00-add-target-group-type-to-response-headers" --head "ec2-to-ecs-01-assess-ec2-to-ecs-migration" --title "Assess AMIgo EC2 to ECS migration" --body-file "docs/ec2-to-ecs/01-assess-ec2-to-ecs-migration.md"
```
