-- The user's acceptance of the Terms of Use and the Privacy Policy (LGPD L15), with the version of the
-- text they accepted. When the texts change, the version in wallet.legal.terms-version changes, and
-- everyone whose terms_version differs is asked again. Empty for the accounts that exist today: they
-- are asked at their next sign-in.
ALTER TABLE users ADD COLUMN terms_version VARCHAR(32);
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMPTZ;
