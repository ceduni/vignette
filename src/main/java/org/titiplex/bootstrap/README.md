# `bootstrap/` — startup tasks

`BootstrapData` runs once when the application starts:

- loads the roles (`RolesService`) and creates the Glottolog settings row if missing;
- creates (or promotes) the bootstrap admin account (in `dev`: `admin` / `admin12345`, see `app.bootstrap.admin.*`);
- on an empty language table, starts the first Glottolog import through the Python worker
  (`service/GlottologPythonBootstrapService`).

Everything here must be idempotent: it runs at every start.
