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

def publishResults() {
    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true//, testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    /**recordIssues enabledForFailure: true, tools: [mavenConsole(), java(), javaDoc()]
    recordIssues enabledForFailure: true, tool: checkStyle()
    recordIssues enabledForFailure: true, tool: spotBugs()*/    
    step( [ $class: 'JacocoPublisher' ] )
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
        parameters([
            string(name: 'MAJOR_VERSION_NUMBER', defaultValue: '8.0.0-SNAPSHOT', description: 'Snapshots will skip Tag stage', trim: true),
            string(name: 'RUN_TESTSUITE', defaultValue: true, description: 'Whether the testsuite should run or not', trim: true),
            string(name: 'FORK_COUNT', defaultValue: '30', description: 'Number of forks to run the testsuite', trim: true),
            string(name: 'RUN_PERF_TESTS', defaultValue: true, description: 'Whether the performance tests should run or not', trim: true)
        ])
    ])

    stage ('Init') {
        if (isSnapshot()) {
            echo "SNAPSHOT detected, skip Tag stage"
        }
        def newVersion = sh (
                script: 'gpg --version',
                returnStdout: true
            ).trim()        
        echo "${newVersion}"
        echo "Installing SCTP"
        sh 'sudo apt update & sudo apt -y install libsctp-dev'
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
        stage("TCK & Testsuite") {
            runTestsuite("${FORK_COUNT}" , "parallel-testing")
        }
     
        stage("PublishResults") {
            publishResults()
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
        stage("PerformanceTests") {
            sh jain-sip-performance/src/main/resources/buildPerfcorder.sh
        }
    } else {
        echo "RUN_PERF_TESTS is false, skipped PerformanceTests stage"
    }
}