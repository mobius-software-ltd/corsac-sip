#!/bin/bash

if [[ -z $RESULTS_DIR ]]; then
  export RESULTS_DIR=$WORKSPACE/results-dir
fi
if [[ -z $TOOLS_DIR ]]; then
  export TOOLS_DIR=$WORKSPACE/report-tools
fi
#sudo apt-get install -y  --force-yes --fix-missing tshark
#sudo apt-get install -y  --force-yes --fix-missing dstat
##################################
echo "start data collection:$@"
##################################
cd $TOOLS_DIR
bash ./pc_start_collect.sh $PERFCORDER_START_OPTIONS "$@"
cd $WORKSPACE
