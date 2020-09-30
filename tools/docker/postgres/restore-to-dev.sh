#!/usr/bin/env sh

psql \
  -U tournmgmt -h db-postgresql-lon1-96765-do-user-6869887-0.a.db.ondigitalocean.com -p 25060 \
  -d tournmgmt-dev -f /mnt/backups/backup.sql
