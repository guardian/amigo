Run the command below from the repository root with
`ec2-to-ecs-05-build-and-publish-docker-image` checked out, after pushing both
the base and step branches:

```shell
gh pr create --base "ec2-to-ecs-04-prepare-amigo-runtime-for-ecs" --head "ec2-to-ecs-05-build-and-publish-docker-image" --title "Build and publish AMIgo Docker image" --body-file "docs/ec2-to-ecs/05-build-and-publish-docker-image.md"
```
