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
chmod 777 $TOOLS_DIR/src/test/resources/*.sh
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
cp $TOOLS_DIR/target/sipp-report-*with-dependencies.jar $TOOLS_DIR/src/test/resources/
export PERFCORDER_HOME=$TOOLS_DIR/src/test/resources/
#echo "listing JDK Lib directory $(ls $JAVA_HOME/lib)"
$TOOLS_DIR/src/test/resources/pc_start_collect.sh $PERFCORDER_START_OPTIONS "$@"
