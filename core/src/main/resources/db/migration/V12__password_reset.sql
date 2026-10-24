-- "Esqueci minha senha": the e-mailed link that sets a new password is one more kind of email_tokens
-- row. V10 limited purpose to the two kinds it had; PostgreSQL named that inline CHECK
-- email_tokens_purpose_check.
ALTER TABLE email_tokens DROP CONSTRAINT email_tokens_purpose_check;
ALTER TABLE email_tokens ADD CONSTRAINT email_tokens_purpose_check
    CHECK (purpose IN ('CONFIRM_ACCOUNT', 'CHANGE_EMAIL', 'PASSWORD_RESET'));
