#!/bin/bash
##################################
echo "=== Tuning OS ==="
##################################
set +e
! sudo sh -c "cat ./sysctl.conf > /etc/sysctl.conf"
! sudo sysctl -p
! sudo sh -c "ulimit -n 65535 && exec su jenkins"
