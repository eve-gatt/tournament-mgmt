#!/usr/bin/env sh

pg_dump -O -x -c \
  -U tournmgmt -h ${PGHOST} -p 25060 \
  -d tournmgmt >/mnt/backups/backup.sql
