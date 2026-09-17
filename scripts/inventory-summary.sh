#!/bin/bash

for g in loadbalancers control_plane workers database nfs_nodes k3s_nodes; do
    printf "%-16s : " "$g"
    ansible "$g" -i inventory/hosts.ini --list-hosts 2>/dev/null \
      | tail -n +2 | xargs
done
