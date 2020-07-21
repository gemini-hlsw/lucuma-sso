
-- GPO-SSO initial schema

-- there are three kinds of users
CREATE TYPE gpp_user_type AS ENUM ('guest', 'standard', 'service');

-- standard users have zero or more standard roles
CREATE TYPE gpp_role_type AS ENUM ('pi', 'ngo', 'staff', 'admin');

-- ngo roles have an associated ngo
CREATE TYPE gpp_ngo AS ENUM ('ar', 'br', 'ca', 'cl', 'gt', 'kr', 'lp', 'uh', 'us');

-- ORCID iD has a format we should validate
CREATE DOMAIN gpp_orcid_id AS character varying; -- TODO format check

-- every user has one row here
CREATE TABLE gpp_user (

  user_id                SERIAL PRIMARY KEY,
  user_enabled           BOOLEAN NOT NULL DEFAULT true,
  user_type              gpp_user_type NOT NULL,
  service_name           VARCHAR, -- non-null iff user_type = 'service'
  role_id       INTEGER, -- BUT WAIT LET'S MAKE IT NON-NULL AND MAKE THE FK CONSTRAINT DEFERRABLE

  orcid_id               gpp_orcid_id UNIQUE,
  orcid_access_token     VARCHAR,
  orcid_token_expiration TIMESTAMP,
  orcid_given_name       VARCHAR,
  orcid_credit_name      VARCHAR,
  orcid_family_name      VARCHAR,
  orcid_email            VARCHAR,

  -- so we can be referenced by gpp_role to ensure only standard users have roles
  UNIQUE (user_id, user_type),

  -- service users must have a service name, others must not
  CHECK ((user_type =  'service' AND service_name IS NOT NULL)
      OR (user_type != 'service' AND service_name IS NULL)),

  -- guest and service users can't have an ORCID iD, but others must have one
  CHECK (((user_type =  'guest' OR  user_type = 'service') AND orcid_id IS NULL)
     OR   (user_type != 'guest' AND user_type != 'service' AND orcid_id IS NOT NULL)),

  -- service names must be unique
  UNIQUE (service_name)

);

-- every standard user has zero to 12 rows here: pi, staff, admin, and one for each of 9 NGOs
CREATE TABLE gpp_role (

  role_id       SERIAL            PRIMARY KEY,
  user_id       INTEGER           NOT NULL,
  user_type     gpp_user_type     NOT NULL,
  role_type     gpp_role_type NOT NULL,
  role_ngo      gpp_ngo,

  -- this check+FK ensure that only standard users have rows here
  CHECK (user_type = 'standard'),
  FOREIGN KEY (user_id, user_type)
  REFERENCES gpp_user (user_id, user_type)
  MATCH FULL
  ON UPDATE CASCADE
  ON DELETE CASCADE,

  -- ngo roles must have an ngo, others must not
  CHECK ((role_type =  'ngo' AND role_ngo IS NOT NULL)
      OR (role_type != 'ngo' AND role_ngo IS NULL)),

  -- so we can be referenced from gpp_user
  UNIQUE (user_id, role_id)

);

-- now we can set up the user's active role
ALTER TABLE gpp_user ADD CONSTRAINT gpp_user_role_fk
FOREIGN KEY (user_id, role_id) REFERENCES gpp_role (user_id, role_id)
ON UPDATE CASCADE
ON DELETE SET NULL;

-- ngo roles must be unique per-user
CREATE UNIQUE INDEX gpp_user_role_unique_ngo ON gpp_role (user_id, role_type, role_ngo)
WHERE role_ngo IS NOT NULL;

-- non-ngo roles must be unique per-user
CREATE UNIQUE INDEX gpp_user_role_unique_other ON gpp_role (user_id, role_type)
WHERE role_ngo IS NULL;


