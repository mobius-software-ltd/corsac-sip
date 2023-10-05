#!/bin/bash
if [[ -z $RESULT_SUFFIX ]]; then
  export RESULT_SUFFIX=
fi
if [[ -z $RESULTS_DIR ]]; then
  export RESULTS_DIR=$WORKSPACE/results-dir
fi
if [[ -z $TOOLS_DIR ]]; then
  export TOOLS_DIR=$WORKSPACE/sipp-report-tool
fi

if [[ -z $GOALS_FILE ]]; then
  export GOALS_FILE=$WORKSPACE/jain-sip-performance/src/main/resources/jSIP-Performance-UAS.xsl
fi

if [[ -z $ANALYSIS_TRIM_PERCENTAGE ]]; then
  export ANALYSIS_TRIM_PERCENTAGE=5
fi

##################################
echo "copy protocol files if defined"
##################################
if [[ -n $PERFCORDER_SEAGULL_CSV ]]; then
cp $PERFCORDER_SEAGULL_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/diameter/seagull-client-stat.csv
fi
if [[ -n $PERFCORDER_SIPP_CSV ]]; then
cp $PERFCORDER_SIPP_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/sip/sipp.csv
fi
if [[ -n $PERFCORDER_JMETER_CSV ]]; then
cp $PERFCORDER_JMETER_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/http/jmeter.csv
fi
if [[ -n $PERFCORDER_MAPP_CSV ]]; then
cp $PERFCORDER_MAPP_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/map/mapp.csv
fi
if [[ -n $PERFCORDER_SMPP_CSV ]]; then
cp $PERFCORDER_SMPP_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/smpp/smppp.csv
fi
if [[ -n $PERFCORDER_TCAP_CSV ]]; then
cp $PERFCORDER_TCAP_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/tcap/tcap.csv
fi
##temporary for msquad per test
if [[ -n $PERFCORDER_MAP_MO_MT_CSV ]]; then
cp $PERFCORDER_MAP_MO_MT_CSV $WORKSPACE/target$RESULT_SUFFIX/data/periodic/map-mo-mt/map-mo-mt.csv
fi

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PERFCORDER_HOME=$TOOLS_DIR/src/main/resources/

##################################
echo "Collect results"
##################################

#remove previous collections, so later we match only one file
rm -rf $TOOLS_DIR/*.zip
$TOOLS_DIR/src/main/resources/pc_stop_collect.sh "$@" -f -d -o $WORKSPACE/target $PERFCORDER_STOP_OPTIONS
###this command is supposed to match just one file
cp -f $WORKSPACE/perf*.zip $RESULTS_DIR/perfTest-$RESULT_SUFFIX.zip
### Check for performance regression
$TOOLS_DIR/src/main/resources/pc_analyse.sh $RESULTS_DIR/perfTest-$RESULT_SUFFIX.zip $ANALYSIS_TRIM_PERCENTAGE > $RESULTS_DIR/PerfCorderAnalysis$RESULT_SUFFIX.xml 2> $RESULTS_DIR/analysis$RESULT_SUFFIX.log
cat $RESULTS_DIR/PerfCorderAnalysis$RESULT_SUFFIX.xml | $TOOLS_DIR/src/main/resources/pc_test.sh  $GOALS_FILE > $RESULTS_DIR/TEST-PerfCorderAnalysisTest$RESULT_SUFFIX.xml 2> $RESULTS_DIR/test$RESULT_SUFFIX.log
cat $RESULTS_DIR/PerfCorderAnalysis$RESULT_SUFFIX.xml | $TOOLS_DIR/src/main/resources/pc_html_gen.sh > $RESULTS_DIR/PerfCorderAnalysis$RESULT_SUFFIX.html 2> $RESULTS_DIR/htmlgen$RESULT_SUFFIX.log
###copy resulting HTML graphs
cp -R ./graphs $RESULTS_DIR/
cd $WORKSPACE
rm -f $RESULTS_DIR/perfTest-$RESULT_SUFFIX.zip
