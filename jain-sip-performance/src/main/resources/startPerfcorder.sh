#!/bin/bash

if [[ -z $RESULTS_DIR ]]; then
  export RESULTS_DIR=$WORKSPACE/results-dir
fi
if [[ -z $TOOLS_DIR ]]; then
  export TOOLS_DIR=$WORKSPACE/sipp-report-tool
fi
#sudo apt-get install -y  --force-yes --fix-missing tshark
#sudo apt-get install -y  --force-yes --fix-missing dstat
##################################
echo "start data collection:$@"
##################################
chmod 777 $TOOLS_DIR/src/main/resources/*.sh
$TOOLS_DIR/src/main/resources/pc_start_collect.sh $PERFCORDER_START_OPTIONS "$@"
