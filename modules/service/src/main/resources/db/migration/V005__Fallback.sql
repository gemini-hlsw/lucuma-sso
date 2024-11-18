-- Add fallback user profile information.
ALTER TABLE lucuma_user
  ADD COLUMN fallback_given_name  VARCHAR,
  ADD COLUMN fallback_credit_name VARCHAR,
  ADD COLUMN fallback_family_name VARCHAR,
  ADD COLUMN fallback_email       VARCHAR;