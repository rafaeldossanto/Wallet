-- Creates the local development database for the Wallet core.
-- Safe to run more than once: it only creates what does not exist yet.
--
-- Run as the Postgres superuser, choosing the password on the command line so it
-- never lands in a file:
--
--   psql -U postgres -h localhost -v wallet_password=YOUR_PASSWORD -f core/db/setup-local.sql
--
-- Then set the same password in the WALLET_DB_PASSWORD environment variable.
-- The schema itself is created by Flyway when the core starts.

SELECT format('CREATE ROLE wallet LOGIN PASSWORD %L', :'wallet_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'wallet')
\gexec

SELECT 'CREATE DATABASE wallet_dev OWNER wallet ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wallet_dev')
\gexec
