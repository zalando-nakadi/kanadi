## Contributing

Firstly, for any feature please make sure you test it, if you can't test the feature then you need to justify why.
Templates for tests can be seen at [here](https://github.com/zalando-nakadi/kanadi/tree/master/src/test/scala). For new
features, its good to ask before starting work just so we can provide clarity as to whether we would eventually accept
the feature request. Opening PR's as soon as possible is also recommended so that we have an early impression of what
you are trying to accomplish (also so we can guide and help you).

Try not to add dependencies unless you need to, we try and restrict Kanadi to have the smallest set of dependencies as
possible to increase adoption and prevent possible dependency hell friction.

Commit messages should follow the style described [here](https://chris.beams.io/posts/git-commit/).

The project is formatted with [scalafmt](https://scalameta.org/scalafmt/). Please format your code before you commit it.

### Running Nakadi locally
There is a [docker-compose file](https://github.com/zalando-nakadi/kanadi/blob/master/docker-compose.yml) which you can use in
conjunction with [docker-compose](https://docs.docker.com/compose/) to run Nakadi locally which is required for running tests. Unfortunately due to limitations
of github docker container registry 
(see [issue](https://github.community/t/docker-pull-from-public-github-package-registry-fail-with-no-basic-auth-credentials-error/16358/68))
you need to login to githubs container registry.

This means that in order to pull the necessary images you need to create a [github personal access token](https://github.com/settings/tokens) 
with the scope `read:packages`. After this you need to login to docker for github container registry, i.e.

```shell
docker login -u <github username> -p <personal access token> docker.pkg.github.com
```

After this `docker-compose` should work as expected
