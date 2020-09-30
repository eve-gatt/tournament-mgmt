#!/usr/bin/env sh

psql \
  -U postgres -h host.docker.internal -p 5432 \
  -d postgres -f /mnt/backups/backup.sql
