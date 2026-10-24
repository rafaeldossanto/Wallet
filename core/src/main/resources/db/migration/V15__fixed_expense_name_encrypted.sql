-- The name of a fixed expense is now encrypted (L7: free text, "Psicólogo" says a lot). The stored form
--    is v1: plus the base64 of IV, text and tag; for 80 characters of up to 4 bytes it takes 467
--    characters, so the column is widened. Rows from before stay in plain text until the start-up backfill
--    (FixedExpenseNameBackfill) encrypts them, and read as they are meanwhile.
ALTER TABLE fixed_expenses ALTER COLUMN name TYPE VARCHAR(512);
