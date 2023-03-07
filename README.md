# repo-access-action

[![Kotlin](https://img.shields.io/badge/kotlin-1.8.10-blue.svg)](http://kotlinlang.org)
[![Gradle Build](https://github.com/araqnid/repo-access-action/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/araqnid/repo-access-action/actions/workflows/gradle-build.yml)

GitHub action to synchronise access permissions of an org team's repositories. It reads a configuration JSON file
listing a team's repositories and ensures that other teams are given the specified access permissions.

This project builds with Gradle, just use `./gradlew build` as normal.

A packaged distributable is written into `dist` by running `./gradlew package`.

This action was inspired by https://github.com/mergermarket/github-access

## Configuration

### Action inputs

You must specify the access configuration file to read, and the org and team name of the team that owns the configured
repositories.

| Input name | Description                                             |
|------------|---------------------------------------------------------|
| accessFile | Path to access file within the repository               |
| org        | Organisation owning the team                            |
| team       | Name or slug of the team administering the repositories |

### Access configuration file

A JSON file containing a list of repositories grouped by a common set of access permissions for other teams.

```json
[
  {
    "description": "Readable by all, push only our team",
    "teams": {
      "all": "pull"
    },
    "repos": [
      "ourteam-repo1",
      "ourteam-repo2"
    ]
  },
  {
    "description": "Push access shared with Wizardry team",
    "teams": {
      "all": "pull",
      "wizards": "push"
    },
    "repos": [
      "ourteam-spells"
    ]
  },
  {
    "description": "Repos only usable by our team",
    "teams": {},
    "repos": [
      "ourteam-secret-sauce"
    ]
  }
]
```

## Using the action

An example:

```yaml
jobs:
  repo-access:
    runs-on: ubuntu-latest
    steps:
      - name: Update repo access permissions
        uses: araqnid/repo-access-action@v1
        with:
          accessFile: access.json
          org: ION-Analytics
          team: interface
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```
