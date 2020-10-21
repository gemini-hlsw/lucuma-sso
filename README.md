# LUCUMA-SSO

Single sign-on service and support libries for Lucuma.

## Web Client Flow

### Initialization

- Hit `/api/v1/refresh-token`
  - If you get a `302 Forbidden` you are not logged in.
    - Continue with **Login** below.
  - If you get a `200 Ok`
    - You are logged in.
    - The response body will contain a new JWT.
    - An http-only refresh cookie will also be set.
    - Set a timer to run **Initialization** again one minute before the JWT expires.
    - Continue with **Normal Operation** below.

### Login

The user must be allowed to choose to log in with ORCID or log in as a guest.

#### Guest Login

- Post to `/api/v1/auth-as-guest`
  - The response will be `201 Created` and the body will contain a JWT. An http-only refresh cookie will be set.
    - Continue with **Normal Operation** below.

#### ORCID Login

- Perform a client-side redirect to `/auth/v1/stage1?state=APP_URI`
  - On success the user will be redirected to `APP_URI`.
    - Continue with **Initialization** above.

### Normal Operation

- Store the JWT in a variable and pass it to all API requests in the header as `Authorization: Bearer <jwt>`.
- Decode the JWT body as a `lucuma.core.model.User`.
- If the user has insufficient privileges to view the application, there are three possibilities that should be presented.
    - If the user is currently a Guest, allow the user to upgrade their guest account (**ORCID Login** above).
    - If the user has other roles that _are_ sufficient (via the `otherRoles` member on `StandardUser`) provide a menu that allows the user to select one of these roles and continue with **Set Role** below.
    - Offer the option to log out. Continue with **Log Out** below.
- Display a user menu with the user's `displayName` shown.
  - If the user is a guest, offer an option to log in via ORCID.
  - If the user is a standard user, offer the option to change role.
  - Offer an option to log out.

### Log Out

- POST to `https://sso.lucuma.gemini.edu/api/v1/logout`
- POST to `https://orcid.org/userStatus.json?logUserOut=true` (we need to test this)
- Continue with **Login** above.

### Set Role

- Post to `/api/v1/setRole?role=<role-id>` to switch the user's current role.
  - Be sure to pass the `Authorization` header.
  - The response body will contain a new JWT.
    - Continue with **Normal Operation** above.


## Back-End Service Workflow

> Note: this is not implemented yet.

Add `lucuma-sso-client` as a dependency.

### Initialization

- Get `/api/v1/public-key` and decode the body as a `PublicKey`.
- Pass this value to the `SsoMiddleware` constructor.
- Use this to wrap routes that must be authenticated. `SsoMiddleware` is an `AuthedMiddleware` that will provide a `User`, or will reject the request with `403 Forbidden`.

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

If you try to run `Main` you will find that it barfs because it needs some ORCID configuration. To set this up, sign into [ORCID](http://orcid.org) as yourself, go to **Developer Tools** under the name menu and create an API key with redirect URL `http://localhost:8080/auth/v1/stage2`. This will allow you to test ORCID authentication locally.

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
        "-DLUCUMA_ORCID_CLIENT_ID=APP-XCUB4VY7YAN9U6BH",
        "-DLUCUMA_ORCID_CLIENT_SECRET=265b63e5-a924-4512-a1e8-573fcfefa92d",
      ],
    }
  ]
}
```
