# repo-access-action

GitHub action to synchronise access permissions of an org team's repositories.

This project builds with Gradle, just use `./gradlew build` as normal.

A packaged distributable is written into `dist` by running `./gradlew package`.

## Configuration

## Using the action

An example:

```yaml
jobs:
  repo-access:
    runs-on: ubuntu-latest
    steps:
      - name: Update repo access permissions
        uses: araqnid/repo-access-action@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```
