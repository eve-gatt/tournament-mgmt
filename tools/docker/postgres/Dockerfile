# docker build -t tournmgmt-dev-postgres tools/docker/postgres \
#    && docker run -it --rm \
#         --env-file tools/docker/postgres/env \
#         -v /mnt/d/work/git-checkouts/tournament-mgmt/tools/backups:/mnt/backups \
#         tournmgmt-dev-postgres \
#         /bin/bash

FROM postgres:11

RUN apt update && apt install -y vim dnsutils

ADD backup*.sh /root/
ADD restore*.sh /root/
RUN chmod +x /root/*.sh

WORKDIR /root
