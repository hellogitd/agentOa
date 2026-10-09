#!/bin/sh
set -eu
# Secrets generated as hex, preventing SQL interpolation of special characters.
app=$(cat /run/secrets/db-password)
migration=$(cat /run/secrets/migration-password)
backup=$(cat /run/secrets/backup-password)
for value in "$app" "$migration" "$backup"; do
  case "$value" in *[!a-f0-9]*|'') echo 'Database secrets must be hex' >&2; exit 1;; esac
done
MYSQL_PWD=$(cat /run/secrets/mysql-root-password) mysql -uroot <<SQL
CREATE USER 'agentoa'@'%' IDENTIFIED BY '$app';
CREATE USER 'agentoa_migrate'@'%' IDENTIFIED BY '$migration';
CREATE USER 'agentoa_backup'@'%' IDENTIFIED BY '$backup';
GRANT SELECT, INSERT, UPDATE, DELETE ON agentoa.* TO 'agentoa'@'%';
GRANT ALL PRIVILEGES ON agentoa.* TO 'agentoa_migrate'@'%';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON agentoa.* TO 'agentoa_backup'@'%';
SQL
