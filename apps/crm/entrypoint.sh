#!/bin/sh
set -eu
bun run db:deploy
bun --filter api dev &
exec bun --filter app dev
