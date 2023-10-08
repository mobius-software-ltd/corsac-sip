def runTestsuite(forkCount=1, profile="defaultProfile") {
     withMaven(maven: 'maven-3.6.3',traceability: true) {
        sh "mvn -B -f jain-sip-testsuite/pom.xml  install -DskipUTs=false  -Dmaven.test.failure.ignore=true -Dmaven.test.redirectTestOutputToFile=true -Dfailsafe.rerunFailingTestsCount=1 -DforkCount=\"$forkCount\" "
     }
}


def build() {
    // Run the maven build with in-module unit testing
    try {
        withMaven(maven: 'maven-3.6.3',traceability: true) {
            sh "mvn -B -f pom.xml -Dmaven.test.redirectTestOutputToFile=true clean install -P sctp"
        }
    } catch(err) {
        publishResults()
        throw err
    }
}

def publishTestsuiteResults() {
    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true//, testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    /**recordIssues enabledForFailure: true, tools: [mavenConsole(), java(), javaDoc()]
    recordIssues enabledForFailure: true, tool: checkStyle()
    recordIssues enabledForFailure: true, tool: spotBugs()*/    
    step( [ $class: 'JacocoPublisher' ] )
}

def publishPerformanceTestsResults() {
    archiveArtifacts artifacts:'results-dir/**, *_screen.log'
    publishHTML (target : [allowMissing: false,        
        keepAll: true,
        reportDir: 'results-dir',
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
            string(name: 'MAJOR_VERSION_NUMBER', defaultValue: '8.0.0-SNAPSHOT', description: 'Snapshots will skip Tag stage', trim: true),
            string(name: 'RUN_TESTSUITE', defaultValue: "true", description: 'Whether the testsuite should run or not', trim: true),
            string(name: 'FORK_COUNT', defaultValue: '30', description: 'Number of forks to run the testsuite', trim: true),
            string(name: 'RUN_PERF_TESTS', defaultValue: "true", description: 'Whether the performance tests should run or not', trim: true),
            string(name: 'SIPP_TRANSPORT_MODE', defaultValue: "u1", description: 'transport used at SIPP for performance tests', trim: true),
            string(name: 'TEST_DURATION', defaultValue: "1800", description: 'performance test duration', trim: true),
            string(name: 'CALL_RATE', defaultValue: "400", description: 'calls per second rate', trim: true),
            string(name: 'CALL_LENGTH', defaultValue: "60", description: 'calls per second rate', trim: true),
            string(name: 'JAVA_OPTS', defaultValue: "-Xms6144m -Xmx6144m -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+UseG1GC -XX:+UseStringDeduplication", description: 'JVM Options used for the SIP Stack', trim: true)
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
            runTestsuite("${FORK_COUNT}" , "parallel-testing")
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
        stage("PerformanceTests") {
            echo "Building Perfcorder"
            sh 'git clone -b master https://github.com/RestComm/PerfCorder.git sipp-report-tool'
            withMaven(maven: 'maven-3.6.3',traceability: true) {
                sh 'mvn -DskipTests=true -B -f sipp-report-tool/pom.xml clean install'
            }
            sh'sudo add-apt-repository -y universe'
            sh 'sudo apt update & sudo apt-get -y install dstat zip'            
            echo 'Building Performance Tests'
            //sh 'jain-sip-performance/src/main/resources/buildPerfcorder.sh'
            //sh 'jain-sip-performance/src/main/resources/tuneOS.sh'
            withMaven(maven: 'maven-3.6.3',traceability: true) {
                sh "mvn -DskipTests=true -B -f jain-sip-performance/pom.xml clean install"
            }
            sh '$WORKSPACE/jain-sip-performance/src/main/resources/download-and-compile-sipp.sh'
            //sh 'killall Shootme'
            echo "Starting UAS Process"       
            sh 'mkdir -p $WORKSPACE/results-dir'  
            sh 'sysctl -w net.core.rmem_max=26214400'  
            echo '${JAVA_OPTS}'             
            sh 'java ${JAVA_OPTS} -cp jain-sip-performance/target/*-with-dependencies.jar -DSIP_STACK_PROPERTIES_PATH=$WORKSPACE/jain-sip-performance/src/main/resources/performance/uas/sip-stack.properties performance.uas.Shootme > $WORKSPACE/results-dir/uas-stdout-log.txt&'
            sleep(time:5,unit:"SECONDS") 
            sh '''                
                PROCESS_PID=$(jps | awk \'/Shootme/{print $1}\')
                echo "Shootme Process PID $PROCESS_PID"

                #export TERM=vt100                
                $WORKSPACE/jain-sip-performance/src/main/resources/sipp -v || true

                echo "starting data collection"
                CLASS_HISTO_JOIN_PATH=$WORKSPACE/jain-sip-performance/src/main/resources/class_histo.join
                COLLECTION_INTERVAL_SECS=15
                $WORKSPACE/jain-sip-performance/src/main/resources/startPerfcorder.sh -f $COLLECTION_INTERVAL_SECS -j $CLASS_HISTO_JOIN_PATH $PROCESS_PID
                echo "starting test"                
                SIPP_TRANSPORT_MODE=un
                SIPP_Performance_UAC=$WORKSPACE/jain-sip-performance/src/main/resources/performance-uac.xml
                CALLS=$(( ${CALL_RATE} * ${TEST_DURATION} ))                                
                CONCURRENT_CALLS=$((${CALL_RATE} * ${CALL_LENGTH} * 2 ))
                echo "calls:$CALLS"
                echo "call rate:${CALL_RATE}"
                echo "call length:${CALL_LENGTH}"
                echo "wait time:$WAIT_TIME"
                echo "test duration:$TEST_DURATION"
                echo "concurrent calls:$CONCURRENT_CALLS"                
                $WORKSPACE/jain-sip-performance/src/main/resources/sipp 127.0.0.1:5080 -s receiver -sf $SIPP_Performance_UAC -t ${SIPP_TRANSPORT_MODE} -nd -i 127.0.0.1 -p 5050 -l $CONCURRENT_CALLS -m $CALLS -r ${CALL_RATE} -fd 1 -trace_stat -trace_screen -timeout_error -bg || true
                echo "Actual date: \$(date -u) | Sleep ends at: \$(date -d $TEST_DURATION+seconds -u)"
            '''
            duration="${TEST_DURATION}" as Integer
            sleep_time=duration + 300
            sleep(time:"${sleep_time}",unit:"SECONDS") 
            echo "TEST ENDED"        
            sh '''
                killall sipp || true
                export PERFCORDER_SIPP_CSV="$WORKSPACE/performance-uac*.csv"
                $WORKSPACE/jain-sip-performance/src/main/resources/stopPerfcorder.sh
            '''            
        }

        stage("Publish Performance Tests Results") {
            publishPerformanceTestsResults()
        }
    } else {
        echo "RUN_PERF_TESTS is false, skipped PerformanceTests stage"
    }
}