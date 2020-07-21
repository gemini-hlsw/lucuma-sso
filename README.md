# GPP-SSO

Single sign-on service and support libries for GPP.

## Local Development QuickStart

Use `docker-compose` to wrangle a dev database. It's way easier than dealing with a real installation.

| Command                                                               | Description                                  |
|-----------------------------------------------------------------------|----------------------------------------------|
| `docker-compose up`                                                   | start up the test database                   |
| `docker-compose up -d`                                                | start up the test database in the background |
| `docker-compose run postgres psql -h postgres -d gpp-sso -U postgres` | start up a `psql` shell                      |
| `docker-compose stop`                                                 | stop the test database                       |
| `docker-compose down`                                                 | destroy the database                         |

### Working on the Schema

When the database is **created** the container will run all the migrations in `modules/service/src/main/resources/migration/` in alphabetic order. This means you need to do `down` and then `up` if you make a schema change. It's helpful to run `up` in the foreground (i.e., without `-d`) when you're messing with the schema because an error will cause the database to fail to come up (in which case you need to do `down` and then `up` again).

### Connecting to the Database

You can connect to youe dev database with locally-installed tools like `pgAdmin` and `psql` as follows. Note that it's important to explicitly specify `localhost` as the host, for example `psql -h localhost -d gpp-sso -U postgres`.

| Parameter | Value       |
|-----------|-------------|
| host      | `localhost` |
| port      | `5432`      |
| database  | `gpp-sso`   |
| user      | `postgres`  |
| password  | (none)      |





