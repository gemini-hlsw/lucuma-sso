# LUCUMA-SSO

Single sign-on service and support libries for Lucuma.

## API

There is currently no GraphQL interface. This is pending.

### Authentication

- `GET /auth/stage1?state=APP_URI` authenticates a user via [ORCID](https://orcid.org) and returns them to `APP_URI` in possession of a standard user JWT cookie.
  - If a JWT cookie already exists:
    - Standard and service users are alredy authenticated so this is a no-op.
    - Guest users will be redirected to ORCID for authentication.
      - If the ORCID profile exists, the guest user's data will be transferred (TODO) and the guest user will be deleted.
      - If the ORCID profile is new, the guest user is upgraded to standard.
  - If a JWT cookie does not already exists:
    - The user is redirected to ORCID for authentication.
    - A standard user corresponding with the ORCID profile will be created if necessary.
  - A JWT cookie is issued and the user is redirected to `APP_URI`.

### Queries

- `GET /api/v1/whoami` returns 403 if the user is not logged in, otherwise a JSON-encoded `lucuma.sso.model.User`.
- `GET /api/v1/publicKey` returns the server's ASCII-armored public key (BCPG), readable by `lucuma.sso.client.util.GpgPublicKeyReader`. Services will read this value on startup and use it to validate JWTs.

### Mutations

- `POST /api/v1/authAsGuest` issues a JWT cookie authenticating the user as a guest, and returns the guest as a JSON-encoded `lucuma.sso.model.User`.
- `POST /api/v1/logout` removes the JWT cookie, if any, and returns nothing of interest.

- TODO `POST /api/v1/setRole` with `role=ROLE_ID` as a form parameter will set the current user's role to `ROLE_ID`. Returns 403 if the user is not logged in, not a standard user, or does not own the specified role; otherwise issues a new JWT cookie and returns the updated JSON-encoded `lucuma.sso.model.User`.

## Web Client Workflow

This is speculative, not fully implemented yet.

Applications should follow this workflow when loaded into the user's browser.

1. `GET /api/v1/whoami` to see if the user is logged in.
1. If not, there are up to two possible options.
    - _Continue as Guest_ (only if allowed by the application). If the user selects this option, `POST /api/v1/authAsGuest` to receive a valid cookie containing a new guest user, and return to (1) above.
    - _Log in via ORCID_. If the user selects this option, do a client-sider redirect to `/auth/stage1?state=APP_URI`. On successful authentication the user will return to (1) above with a valid user cookie.
1. If the user has insufficient privileges to view the application, there are three possibilities:
    - _Log in via ORCID_. Present this if the user is currently a Guest, with the same link as above, to allow the user to upgrade their guest account. Continue at (1) above.
    - _Change Role_. If the user has other roles that _are_ sufficient (via the `otherRoles` member on `StandardUser`) provide a menu that allows the user to select one of these roles and make an `POST` call to `/api/v1/setRole` to receive an upgraded JWT. Continue at (1) above.
    - _Change User_. Offer the option to log out and log back in as someone else. To do this you must hit `https://sso.lucuma.gemini.edu/api/v1/logout` _and_ `https://orcid.org/userStatus.json?logUserOut=true` and then continue with (1) above.
1. Continue with application startup.

Applications should provide a user menu displaying the user's `displayName`, providing the following options:

- _ORCID Profile_ (standard users only). Open ORCID link in a new tab.
- _Change Role_ (when possible, see (4) above).
- _Log Out_ (see (4) above).

Applications should examine the cookie periodically while the application is running (once per second perhaps). The cookie will be renewed periodically through normal server interaction, so you can expect the content to change. However if the *user* changes you must present the user with a dialog saying something like _You have logged in elsewhere as [name]_, with no option but to reload the page and restart at (1) above, or possibly reset the application without reload and return to (2).

## Back-End Service Workflow

This is speculative, not fully implemented yet.

- The `lucuma-sso-client` library will provide an `AuthMiddleware[F, User]` that decodes the request JWT and passes the user to to the request handler. If necessary the JWT will be renwewed asynchronously while the request is processed, and the cookie will be replaced in the response.
- If the request processing requires that a downstream call be made to another LUCUMA service, the cookie can be lifted from the request and passed forward. We also need to pass trace headers, so it might make sense for the middleware to also provide an HTTP client. TBD.

## Local Development QuickStart

Use `docker-compose` to wrangle a dev database. It's way easier than dealing with a real installation.

| Command                                                               | Description                                    |
|-----------------------------------------------------------------------|------------------------------------------------|
| `docker-compose up`                                                   | start up the test database                     |
| `docker-compose up -d`                                                | start up the test database in the background   |
| `docker-compose run postgres psql -h postgres -d lucuma-sso -U jimmy` | start up a `psql` shell (password is `banana`) |
| `docker-compose stop`                                                 | stop the test database                         |
| `docker-compose down`                                                 | destroy the database                           |


### Working on the Schema

When the database is **created** the container will run all the migrations in `modules/service/src/main/resources/db/migration/` in alphabetic order. This means you need to do `down` and then `up` if you make a schema change. It's helpful to run `up` in the foreground (i.e., without `-d`) when you're messing with the schema because an error will cause the database to fail to come up (in which case you need to do `down` and then `up` again).

The app runs these migrations on startup as well, so you don't need to do the down/up dance if you're running the app.

### Connecting to the Database

You can connect to youe dev database with locally-installed tools like `pgAdmin` and `psql` as follows. Note that it's important to explicitly specify `localhost` as the host, for example `psql -h localhost -d lucuma-sso -U jimmy`.

| Parameter | Value        |
|-----------|--------------|
| host      | `localhost`  |
| port      | `5432`       |
| database  | `lucuma-sso` |
| user      | `jimmy`      |
| password  | `banana`     |

### Setting up ORCID Credentials

If you try to run `Main` you will find that it barfs because it needs some ORCID configuration. To set this up, sign into [ORCID](http://orcid.org) as yourself, go to **Developer Tools** under the name menu and create an API key with redirect URL `http://localhost:8080/auth/stage2`. This will allow you to test ORCID authentication locally.

You will need to provide `GPP_ORCID_CLIENT_ID` and `GPP_ORCID_CLIENT_SECRET` either as environment variables or system properties when you run `Main`. To do this in VS-Code you can hit F5 and then set up a run configuration as follows after which hitting F5 again should run SSO locally. Output will be in the Debug Console window.

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type"       : "scala",
      "request"    : "launch",
      "name"       : "Lucuma SSO",
      "mainClass"  : "lucuma.sso.service.Main",
      "args"       : [],
      "buildTarget": "service",
      "jvmOptions" : [
        "-DGPP_ORCID_CLIENT_ID=...",
        "-DGPP_ORCID_CLIENT_SECRET=..."
      ],
    }
  ]
}
```
