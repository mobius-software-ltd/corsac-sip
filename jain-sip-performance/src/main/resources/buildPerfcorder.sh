#!/bin/bash

#TODO this should be part of Amazon image
#sudo apt-get -y --fix-missing install dstat

if [[ -z $RESULTS_DIR ]]; then
  export RESULTS_DIR=$WORKSPACE/results-dir
fi
if [[ -z $TOOLS_DIR ]]; then
  export TOOLS_DIR=$WORKSPACE/report-tools
fi
if [[ -z $PC_BRANCH_NAME ]]; then
  PC_BRANCH_NAME=master
fi

##################################
echo "Prepare directories, clean and create"
##################################
rm -fr $TOOLS_DIR
mkdir -p $TOOLS_DIR
rm -fr $RESULTS_DIR
mkdir -p $RESULTS_DIR

##################################
echo "Use telestax maven settings"
##################################
cp $WORKSPACE/telscale-commons/artifactory/settings.xml -f ~/.m2/settings.xml

##################################
echo "Prepare monitoring tool"
##################################
cd $TOOLS_DIR
rm -rf sipp-report-tool
git clone -b $PC_BRANCH_NAME https://github.com/RestComm/PerfCorder.git sipp-report-tool
cd sipp-report-tool
mvn -q clean install
cp -r target/classes/* $TOOLS_DIR/
chmod 777 $TOOLS_DIR/*.sh
cp target/sipp-report-*with-dependencies.jar $TOOLS_DIR/
cd $WORKSPACE

if [[ -z $COLLECTION_INTERVAL_SECS ]]; then
  export COLLECTION_INTERVAL_SECS=10
fi
