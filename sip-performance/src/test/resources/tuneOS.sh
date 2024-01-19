#!/bin/bash
##################################
echo "=== Tuning OS ==="
##################################
set +e
! sudo sh -c "cat $WORKSPACE/jain-sip-performance/src/test/resources/sysctl.conf > /etc/sysctl.conf"
! sudo sysctl -p
## ! sudo sh -c "ulimit -n 65535 && exec su jenkins"
#! sudo sh -c "ulimit -n 65535"
