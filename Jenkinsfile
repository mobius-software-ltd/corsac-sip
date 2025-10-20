def runTestsuite(enableNetty, forkCount=1, testToRun, profile="defaultProfile") {
    if (testToRun == "all") {    
        echo "Running the entire testsuite"
        withMaven(maven: 'maven-3.6.3',traceability: true) {
            sh "mvn -B -f sip-testsuite/pom.xml  clean install -DskipUTs=false  -Dmaven.test.failure.ignore=true -Dmaven.test.redirectTestOutputToFile=true -Dfailsafe.rerunFailingTestsCount=1 -DenableNetty=\"$enableNetty\" -DforkCount=\"$forkCount\" "
        }
    } else {
        echo "Running the testsuite for $testToRun"
        withMaven(maven: 'maven-3.6.3',traceability: true) {
            sh "mvn -B -f sip-testsuite/pom.xml  clean install -DskipUTs=false  -Dmaven.test.failure.ignore=true -Dmaven.test.redirectTestOutputToFile=true -Dfailsafe.rerunFailingTestsCount=1 -DenableNetty=\"$enableNetty\" -DforkCount=\"$forkCount\" -Dtest=\"$testToRun\""
        }
    }
}


def build() {
    // Run the maven build with in-module unit testing
    try {
        withMaven(maven: 'maven-3.6.3',traceability: true) {
            sh "mvn -DskipTests=true -B -f pom.xml -Dmaven.test.redirectTestOutputToFile=true clean install -P sctp"
        }
    } catch(err) {
        publishTestsuiteResults()
        throw err
    }
}

def publishTestsuiteResults() {
    junit testResults: '**/target/surefire-reports/*.xml', keepLongStdio: true, allowEmptyResults: true//, testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    /**recordIssues enabledForFailure: true, tools: [mavenConsole(), java(), javaDoc()]
    recordIssues enabledForFailure: true, tool: checkStyle()
    recordIssues enabledForFailure: true, tool: spotBugs()*/    
    // step( [ $class: 'JacocoPublisher' ] )
}

def publishPerformanceTestsResults(dir) {
    echo "\"$dir\""
    sh "mv *_screen.log \"$dir\""
    archiveArtifacts artifacts:"$dir/**"
    publishHTML (target : [allowMissing: false,        
        keepAll: true,
        reportDir: "$dir",
        reportFiles: 'PerfCorderAnalysis.html',
        reportName: 'PerfCorder Analysis'])
}

def tag() {
    // Save release version
    def pom = readMavenPom file: 'pom.xml'
    releaseVersion = pom.version
    echo "Tagging: Set release version to ${releaseVersion}"

    withCredentials([usernamePassword(credentialsId: 'c2cce724-a831-4ec8-82b1-73d28d1c367a', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh('git fetch https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/telestax/telscale-jsip.git')
        sh("git commit -a -m \"New release candidate ${releaseVersion}\"")
        sh("git tag ${releaseVersion}")
        sh('git push https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/telestax/telscale-jsip.git --tags')
    }
}

def version() {
    def newVersion = "${MAJOR_VERSION_NUMBER}"
    if (BRANCH_NAME != "master" && !BRANCH_NAME.startsWith("rel")) {
        newVersion = "${MAJOR_VERSION_NUMBER}.${BRANCH_NAME}"
    }
    currentBuild.displayName = "#${BUILD_NUMBER}-${newVersion}"
    withMaven(maven: 'maven-3.6.3',traceability: true) {
        sh "mvn -B versions:set -DnewVersion=${newVersion} versions:commit"
    }
    
}

def isSnapshot() {
    return "${params.MAJOR_VERSION_NUMBER}".contains("SNAPSHOT")
}

node("slave-xlarge") {
    properties([
        buildDiscarder(logRotator(daysToKeepStr: '10', numToKeepStr: '10')),
        parameters([
            string(name: 'MAJOR_VERSION_NUMBER', defaultValue: '10.0.0-SNAPSHOT', description: 'Snapshots will skip Tag stage', trim: true),
            string(name: 'RUN_TESTSUITE', defaultValue: "true", description: 'Whether the testsuite should run or not', trim: true),
            string(name: 'ENABLE_NETTY', defaultValue: "true", description: 'Whether the testsuite should run using Netty', trim: true),
            string(name: 'FORK_COUNT', defaultValue: '20', description: 'Number of forks to run the testsuite', trim: true),
            string(name: 'TESTS_TO_RUN', defaultValue: "all", description: 'if a single test needs to be running to avoid rerunning the entire testsuite again', trim: true),
            string(name: 'RUN_PERF_TESTS', defaultValue: "false", description: 'Whether the performance tests should run or not', trim: true),
            string(name: 'RUN_UAS_PERF_TESTS', defaultValue: "true", description: 'Whether the UAS performance tests should run or not', trim: true),
            string(name: 'RUN_B2BUA_PERF_TESTS', defaultValue: "true", description: 'Whether the B2BUA performance tests should run or not', trim: true),
            choice(name: 'SIPP_TRANSPORT_MODE', choices: ["u1", "t1", "l1", "un", "tn", "ln"], description: 'transport used at SIPP for performance tests', trim: true),
            string(name: 'SIPP_MAX_SOCKET', defaultValue: "64", description: 'SIPP max socket value for multi socket transports', trim: true),
            string(name: 'UAS_TEST_DURATION', defaultValue: "1800", description: 'UAS performance test duration', trim: true),
            string(name: 'UAS_CALL_RATE', defaultValue: "1000", description: 'UAS calls per second rate', trim: true),
            string(name: 'UAS_CALL_LENGTH', defaultValue: "60", description: 'UAS call length', trim: true),
            string(name: 'B2BUA_TEST_DURATION', defaultValue: "1800", description: 'B2BUA performance test duration', trim: true),
            string(name: 'B2BUA_CALL_RATE', defaultValue: "500", description: 'B2BUA calls per second rate', trim: true),
            string(name: 'B2BUA_CALL_LENGTH', defaultValue: "60", description: 'B2BUA call length', trim: true),
            string(name: 'POST_PERF_ADDITIONAL_SLEEP_TIME', defaultValue: "300", description: 'Additional Sleep time after performance test to ensure proper cleanup', trim: true),
            string(name: 'JAVA_OPTS', defaultValue: "-Xms6144m -Xmx6144m -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+UseG1GC -XX:+UseStringDeduplication", description: 'JVM Options used for the SIP Stack', trim: true),            
            choice(name: 'PERF_JAIN_SIP_RI_VERSION', choices: ["current", "7.0.5.287"], description: 'Version of JAIN SIP RI to use for running perf tests (example: 7.0.5.287)', trim: true),
            string(name: 'PERF_LOG4J_VERSION', defaultValue: "1.2.17", description: 'Version of LOG4J to go with the specific version of JAIN SIP RI to use for running perf tests', trim: true)            
        ])
    ])

    stage ('Init') {
        cleanWs()

        if (isSnapshot()) {
            echo "SNAPSHOT detected, skip Tag stage"
        }
        def newVersion = sh (
                script: 'gpg --version',
                returnStdout: true
            ).trim()        
        echo "${newVersion}"        
        /**if("${newVersion}".contains("2.2.15")) {
            echo "GPG2 already installed"
        } else {
            echo "GPG2 not installed"
            sh 'rm -f install-gnupg22.sh'
            sh 'curl -OL "https://raw.githubusercontent.com/deruelle/miscellaneous/main/install-gnupg22.sh" && sudo -H bash ./install-gnupg22.sh'            
        }
        sh 'gpg --version'
        sh 'gpg --list-keys'
        sh 'gpg --list-secret-keys'
        sh 'gpg --list-sigs'
        sh 'gpg --list-trustdb'*/        
    }
    

    /**configFileProvider(
        [configFile(fileId: 'c33123c7-0e84-4be5-a719-fc9417c13fa3',  targetLocation: 'settings.xml')]) {
	    sh 'mkdir -p ~/.m2 && sed -i "s|@LOCAL_REPO_PATH@|$WORKSPACE/M2_REPO|g" $WORKSPACE/settings.xml && cp $WORKSPACE/settings.xml -f ~/.m2/settings.xml'
    }**/

    stage ('Checkout') {
        checkout scm
    }   

    stage('Versioning') {
        if (!isSnapshot()) {
            version()
        } else {
            echo "SNAPSHOT detected, skip Versioning stage"
        }
    }

    stage ("Build") {
        build()
    }

    if("${params.RUN_TESTSUITE}" == "true") {
        echo "RUN_TESTSUITE is true, running TCK & Testsuite stage"
        echo "Installing SCTP"
        sh 'sudo apt update & sudo apt-get -y install libsctp1'        

        stage("TCK & Testsuite") {
            runTestsuite("${ENABLE_NETTY}", "${FORK_COUNT}", "${TESTS_TO_RUN}", "parallel-testing")
        }
     
        stage("Publish TCK & Testsuite Results") {
            publishTestsuiteResults()
        }
    } else {
        echo "RUN_TESTSUITE is false, skipped TCK & Testsuite stage"
    }

    if ( !isSnapshot()) {
        stage('Tag') {
            tag()
        }
    } else {
        echo "SNAPSHOT detected, skipped Tag stage"
    }

    if("${params.RUN_PERF_TESTS}" == "true") {        
        echo "RUN_PERF_TESTS is true, running Performance Tests stage"

        stage("Init Performance Tests") {
            echo "Installing TLS"
            sh 'sudo apt-get -y install libncurses5'
            sh 'wget http://nz2.archive.ubuntu.com/ubuntu/pool/main/o/openssl/libssl1.1_1.1.1f-1ubuntu2_amd64.deb'
            sh 'sudo dpkg -i libssl1.1_1.1.1f-1ubuntu2_amd64.deb'
            sh 'openssl req -newkey rsa:2048 -x509 -keyout cakey.pem -out cacert.pem -days 3650 -nodes -subj "/C=UA/L=Kiev/O=Mobius Software LTD/CN=www.mobius-software.com"'

            echo "Building Perfcorder"
            sh 'git clone -b master https://github.com/RestComm/PerfCorder.git sipp-report-tool'
            withMaven(maven: 'maven-3.6.3',traceability: true) {
                sh 'mvn -DskipTests=true -B -f sipp-report-tool/pom.xml clean install'
            }
            sh'sudo add-apt-repository -y universe'
            sh 'sudo apt update & sudo apt-get -y install dstat zip'            
            echo 'Building Performance Tests'
            //sh 'sip-performance/src/test/resources/buildPerfcorder.sh'
            //sh 'sip-performance/src/test/resources/tuneOS.sh'
            withMaven(maven: 'maven-3.6.3',traceability: true) {
                sh "mvn -DskipTests=true -B -f sip-performance/pom.xml clean install"
            }
            sh '$WORKSPACE/sip-performance/src/test/resources/download-and-compile-sipp.sh'
            sh 'sudo sysctl -w net.core.rmem_max=26214400'  
            sh 'ulimit -n 120000'
        }
        if("${params.RUN_UAS_PERF_TESTS}" == "true") {
            echo "RUN_UAS_PERF_TESTS is true, running UAS Performance Tests stage"            

            stage("UAS Performance Tests") {
                
                //sh 'killall Shootme'
                echo "Starting UAS Process"                       
                sh 'mkdir -p $WORKSPACE/perf-results-dir-uas'                  
                echo 'JAVA OPTS: ' + "${JAVA_OPTS}"   
                echo 'PERF_JAIN_SIP_RI_VERSION: ' + "${PERF_JAIN_SIP_RI_VERSION}" 
                echo 'PERF_LOG4J_VERSION: ' + "${params.PERF_LOG4J_VERSION}"     
                if("${params.PERF_JAIN_SIP_RI_VERSION}" == "current") {                          
                    sh '''
                        export CLASSPATH="sip-ri/target/*:sip-api/target/*:sip-performance/target/*"
                        echo "CLASSPATH: " + \"${CLASSPATH}\"
                        java ${JAVA_OPTS} -cp ${CLASSPATH} -DSIP_STACK_PROPERTIES_PATH=$WORKSPACE/sip-performance/src/test/resources/performance/uas/sip-stack.properties -Djavax.net.ssl.trustStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStorePassword=passphrase -Djavax.net.ssl.keyStoreType=jks performance.uas.Shootme > $WORKSPACE/perf-results-dir-uas/uas-stdout-log.txt&
                    '''
                } else {
                    sh '''
                        export CLASSPATH="sip-performance/src/test/resources/sip-ri-backups/sip-ri-"${PERF_JAIN_SIP_RI_VERSION}".jar:sip-performance/src/test/resources/sip-ri-backups/log4j-"${PERF_LOG4J_VERSION}".jar:sip-api/target/*:sip-performance/target/*"
                        echo "CLASSPATH: " + \"${CLASSPATH}\"
                        java ${JAVA_OPTS} -cp $CLASSPATH -DSIP_STACK_PROPERTIES_PATH=$WORKSPACE/sip-performance/src/test/resources/performance/uas/sip-stack-legacy.properties -Djavax.net.ssl.trustStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStorePassword=passphrase -Djavax.net.ssl.keyStoreType=jks performance.uas.Shootme > $WORKSPACE/perf-results-dir-uas/uas-stdout-log.txt&
                    '''
                }
                sleep(time:5,unit:"SECONDS") 
                sh '''                
                    PROCESS_PID=$(jps | awk \'/Shootme/{print $1}\')
                    echo "Shootme Process PID $PROCESS_PID"

                    #export TERM=vt100                                    

                    echo "starting data collection"
                    RESULTS_DIR=$WORKSPACE/perf-results-dir-uas
                    CLASS_HISTO_JOIN_PATH=$WORKSPACE/sip-performance/src/test/resources/class_histo.join
                    COLLECTION_INTERVAL_SECS=15
                    $WORKSPACE/sip-performance/src/test/resources/startPerfcorder.sh -f $COLLECTION_INTERVAL_SECS -j $CLASS_HISTO_JOIN_PATH $PROCESS_PID
                    echo "starting test"                                
                    SIPP_Performance_UAC=$WORKSPACE/sip-performance/src/test/resources/performance/uas/performance-uac.xml
                    CALLS=$(( ${UAS_CALL_RATE} * ${UAS_TEST_DURATION} ))                                
                    CONCURRENT_CALLS=$((${UAS_CALL_RATE} * ${UAS_CALL_LENGTH} * 2 ))
                    TARGET_PORT=5080
                    MAX_SOCKETS=
                    if [ "${SIPP_TRANSPORT_MODE}" = "l1" ]; then 
                        TARGET_PORT=5081                        
                    fi                    
                    if [ "${SIPP_TRANSPORT_MODE}" = "ln" ]; then 
                        TARGET_PORT=5081                        
                    fi
                    if [ "${SIPP_TRANSPORT_MODE}" = "un" ]; then 
                        MAX_SOCKETS="-max_socket ${SIPP_MAX_SOCKET}"
                    fi
                    if [ "${SIPP_TRANSPORT_MODE}" = "tn" ]; then 
                        MAX_SOCKETS="-max_socket ${SIPP_MAX_SOCKET}"
                    fi
                    if [ "${SIPP_TRANSPORT_MODE}" = "ln" ]; then 
                        MAX_SOCKETS="-max_socket ${SIPP_MAX_SOCKET}"
                    fi
                    mv $WORKSPACE/sip-performance/src/test/resources/sipp-tls $WORKSPACE/sip-performance/src/test/resources/sipp
                    $WORKSPACE/sip-performance/src/test/resources/sipp -v || true
                    echo "calls:$TARGET_PORT"
                    echo "sipp max socket:${SIPP_MAX_SOCKET}"
                    echo "calls:$MAX_SOCKETS"
                    echo "calls:$CALLS"
                    echo "call rate:${UAS_CALL_RATE}"
                    echo "call length:${UAS_CALL_LENGTH}"
                    echo "wait time:$WAIT_TIME"
                    echo "test duration:$UAS_TEST_DURATION"
                    echo "concurrent calls:$CONCURRENT_CALLS"                
                    $WORKSPACE/sip-performance/src/test/resources/sipp 127.0.0.1:$TARGET_PORT -s receiver -sf $SIPP_Performance_UAC -t ${SIPP_TRANSPORT_MODE} -nd -i 127.0.0.1 -p 5050 -m $CALLS -r ${UAS_CALL_RATE} -fd 1 -trace_stat -trace_screen -timeout_error $MAX_SOCKETS -bg || true
                    echo "Actual date: \$(date -u) | Sleep ends at: \$(date -d $UAS_TEST_DURATION+seconds -u)"
                '''                
                duration="${UAS_TEST_DURATION}" as Integer
                call_length="${UAS_CALL_LENGTH}" as Integer                
                echo "duration ${duration}"                
                echo "call_length ${call_length}"
                sleep_time=duration + call_length + 10
                echo "sleep_time ${sleep_time}"
                sleep(time:"${sleep_time}",unit:"SECONDS") 
                sh '''
                    pkill -SIGUSR1 sipp || true
                '''
                additional_duration="${POST_PERF_ADDITIONAL_SLEEP_TIME}" as Integer                
                echo "additional_duration ${additional_duration}"
                sleep(time:"${additional_duration}",unit:"SECONDS") 
                echo "TEST ENDED"        
                sh '''
                    pgrep sipp || true
                    killall sipp || true
                    export PERFCORDER_SIPP_CSV="$WORKSPACE/performance-uac*.csv"
                    export GOALS_FILE=$WORKSPACE/sip-performance/src/test/resources/performance/uas/jSIP-Performance-UAS.xsl
                    export RESULTS_DIR=$WORKSPACE/perf-results-dir-uas
                    $WORKSPACE/sip-performance/src/test/resources/stopPerfcorder.sh
                '''            
            }

            stage("Publish UAS Performance Tests Results") {
                publishPerformanceTestsResults("perf-results-dir-uas")
            }
        } else {
            echo "RUN_UAS_PERF_TESTS is false, skipped UAS Performance Tests stage"
        }

        if("${params.RUN_B2BUA_PERF_TESTS}" == "true") {
            echo "RUN_B2BUA_PERF_TESTS is true, running B2BUA Performance Tests stage"
            
            stage("B2BUA Performance Tests") {
                
                //sh 'killall Shootme'
                echo "Starting B2BUA Process"       
                sh 'mkdir -p $WORKSPACE/perf-results-dir-b2bua'                  
                echo 'JAVA OPTS: ' + "${JAVA_OPTS}"   
                echo 'PERF_JAIN_SIP_RI_VERSION: ' + "${PERF_JAIN_SIP_RI_VERSION}"   
                echo 'PERF_LOG4J_VERSION: ' + "${params.PERF_LOG4J_VERSION}"   
                if("${params.PERF_JAIN_SIP_RI_VERSION}" == "current") {                                          
                    sh '''
                        CLASSPATH="sip-ri/target/*:sip-api/target/*:sip-performance/target/*"
                        echo "CLASSPATH: " + \"${CLASSPATH}\"
                        java ${JAVA_OPTS} -cp $CLASSPATH -DSIP_STACK_PROPERTIES_PATH=$WORKSPACE/sip-performance/src/test/resources/performance/b2bua/sip-stack.properties -Djavax.net.ssl.trustStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStorePassword=passphrase -Djavax.net.ssl.keyStoreType=jks performance.b2bua.Test > $WORKSPACE/perf-results-dir-b2bua/b2bua-stdout-log.txt&
                    '''
                } else {
                    sh '''
                        CLASSPATH="sip-performance/src/test/resources/sip-ri-backups/sip-ri-"${PERF_JAIN_SIP_RI_VERSION}".jar:sip-performance/src/test/resources/sip-ri-backups/log4j-"${PERF_LOG4J_VERSION}".jar:sip-api/target/*:sip-performance/target/*"
                        echo "CLASSPATH: " + \"${CLASSPATH}\"
                        java ${JAVA_OPTS} -cp $CLASSPATH -DSIP_STACK_PROPERTIES_PATH=$WORKSPACE/sip-performance/src/test/resources/performance/b2bua/sip-stack-legacy.properties -Djavax.net.ssl.trustStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStore=$WORKSPACE/sip-performance/src/test/resources/performance/testkeys -Djavax.net.ssl.keyStorePassword=passphrase -Djavax.net.ssl.keyStoreType=jks performance.b2bua.Test > $WORKSPACE/perf-results-dir-b2bua/b2bua-stdout-log.txt&
                    '''
                }        
                sleep(time:5,unit:"SECONDS") 
                sh '''                
                    PROCESS_PID=$(jps | awk \'/Test/{print $1}\')
                    echo "B2BUA Process PID $PROCESS_PID"

                    #export TERM=vt100                                    

                    echo "starting data collection"
                    RESULTS_DIR=$WORKSPACE/perf-results-dir-b2bua
                    CLASS_HISTO_JOIN_PATH=$WORKSPACE/sip-performance/src/test/resources/class_histo.join
                    COLLECTION_INTERVAL_SECS=15
                    $WORKSPACE/sip-performance/src/test/resources/startPerfcorder.sh -f $COLLECTION_INTERVAL_SECS -j $CLASS_HISTO_JOIN_PATH $PROCESS_PID
                    echo "starting B2BUA test"                                
                    SIPP_Performance_UAS=$WORKSPACE/sip-performance/src/test/resources/performance/b2bua/uas_DIALOG.xml
                    SIPP_Performance_UAC=$WORKSPACE/sip-performance/src/test/resources/performance/b2bua/uac_DIALOG.xml
                    CALLS=$(( ${B2BUA_CALL_RATE} * ${B2BUA_TEST_DURATION} ))                                
                    CONCURRENT_CALLS=$((${B2BUA_CALL_RATE} * ${B2BUA_CALL_LENGTH} * 2 ))
                    TARGET_PORT=5060
                    MAX_SOCKETS=
                    if [ "${SIPP_TRANSPORT_MODE}" = "l1" ]; then 
                        TARGET_PORT=5061                        
                    fi                    
                    if [ "${SIPP_TRANSPORT_MODE}" = "ln" ]; then 
                        TARGET_PORT=5061                        
                    fi
                    if [ "${SIPP_TRANSPORT_MODE}" = "un" ]; then 
                        MAX_SOCKETS="-max_socket ${SIPP_MAX_SOCKET}"
                    fi
                    if [ "${SIPP_TRANSPORT_MODE}" = "tn" ]; then 
                        MAX_SOCKETS="-max_socket ${SIPP_MAX_SOCKET}"
                    fi
                    if [ "${SIPP_TRANSPORT_MODE}" = "ln" ]; then 
                        MAX_SOCKETS="-max_socket ${SIPP_MAX_SOCKET}"
                    fi
                    mv $WORKSPACE/sip-performance/src/test/resources/sipp-tls $WORKSPACE/sip-performance/src/test/resources/sipp
                    $WORKSPACE/sip-performance/src/test/resources/sipp -v || true
                    echo "calls:$TARGET_PORT"
                    echo "sipp max socket:${SIPP_MAX_SOCKET}"
                    echo "calls:$MAX_SOCKETS"
                    echo "calls:$CALLS"
                    echo "call rate:${B2BUA_CALL_RATE}"
                    echo "call length:${B2BUA_CALL_LENGTH}"
                    echo "wait time:$WAIT_TIME"
                    echo "test duration:$B2BUA_TEST_DURATION"
                    echo "concurrent calls:$CONCURRENT_CALLS"                
                    $WORKSPACE/sip-performance/src/test/resources/sipp -sf $SIPP_Performance_UAS -t ${SIPP_TRANSPORT_MODE} -nd -i 127.0.0.1 -p 5090 -trace_stat -trace_screen -timeout_error $MAX_SOCKETS -bg || true
                    $WORKSPACE/sip-performance/src/test/resources/sipp 127.0.0.1:$TARGET_PORT -s sender -sf $SIPP_Performance_UAC -t ${SIPP_TRANSPORT_MODE} -nd -i 127.0.0.1 -p 5050 -m $CALLS -r ${B2BUA_CALL_RATE} -fd 1 -trace_stat -trace_screen -timeout_error $MAX_SOCKETS -bg || true
                    echo "Actual date: \$(date -u) | Sleep ends at: \$(date -d $B2BUA_TEST_DURATION+seconds -u)"
                '''
                duration="${B2BUA_TEST_DURATION}" as Integer
                call_length="${B2BUA_CALL_LENGTH}" as Integer                
                echo "duration ${duration}"                
                echo "call_length ${call_length}"
                sleep_time=duration + call_length + 10
                echo "sleep_time ${sleep_time}"
                sleep(time:"${sleep_time}",unit:"SECONDS") 
                sh '''
                    pkill -SIGUSR1 sipp || true
                '''
                additional_duration="${POST_PERF_ADDITIONAL_SLEEP_TIME}" as Integer                
                echo "additional_duration ${additional_duration}"
                sleep(time:"${additional_duration}",unit:"SECONDS") 
                echo "TEST ENDED"        
                sh '''
                    pgrep sipp || true
                    killall sipp || true
                    export PERFCORDER_SIPP_CSV="$WORKSPACE/uas_DIALOG*.csv"
                    export GOALS_FILE=$WORKSPACE/sip-performance/src/test/resources/performance/uas/jSIP-Performance-B2BUA.xsl
                    export RESULTS_DIR=$WORKSPACE/perf-results-dir-b2bua
                    $WORKSPACE/sip-performance/src/test/resources/stopPerfcorder.sh
                '''            
            }

            stage("Publish B2BUA Performance Tests Results") {
                publishPerformanceTestsResults("perf-results-dir-b2bua")
            }
        } else {
            echo "RUN_B2BUA_PERF_TESTS is false, skipped B2BUA Performance Tests stage"
        }
    }
}
