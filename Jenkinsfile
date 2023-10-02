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
    junit testResults: '**/target/surefire-reports/*.xml', testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    checkstyle canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '**/checkstyle-result.xml', unHealthy: ''
    step( [ $class: 'JacocoPublisher' ] )
}

def tag() {
    // Save release version
    def pom = readMavenPom file: 'pom.xml'
    releaseVersion = pom.version
    echo "Set release version to ${releaseVersion}"

    withCredentials([usernamePassword(credentialsId: 'c2cce724-a831-4ec8-82b1-73d28d1c367a', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh('git fetch https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/telestax/telscale-jsip.git')
        sh("git commit -a -m \"New release candidate ${releaseVersion}\"")
        sh("git tag ${releaseVersion}")
        sh('git push https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/telestax/telscale-jsip.git --tags')
    }
}

def version() {
    def newVersion = "${params.MAJOR_VERSION_NUMBER}"
    if (BRANCH_NAME != "ts2-c4-mem-issue") {
        newVersion = "${params.MAJOR_VERSION_NUMBER}-${BRANCH_NAME}"
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
            string(name: 'MAJOR_VERSION_NUMBER', defaultValue: '8.0.0-SNAPSHOT', description: 'Snapshots will skip Tag stage', trim: true)
        ])
    ])

    if (isSnapshot()) {
        echo "SNAPSHOT detected, skip Tag stage"
    }

    /**configFileProvider(
        [configFile(fileId: 'c33123c7-0e84-4be5-a719-fc9417c13fa3',  targetLocation: 'settings.xml')]) {
	    sh 'mkdir -p ~/.m2 && sed -i "s|@LOCAL_REPO_PATH@|$WORKSPACE/M2_REPO|g" $WORKSPACE/settings.xml && cp $WORKSPACE/settings.xml -f ~/.m2/settings.xml'
    }**/

    stage ('Checkout') {
        checkout scm
    }   

    stage('Versioning') {
        version()
    }

    stage ("Build") {
        build()
    }

    stage("CITestsuiteParallel") {
            runTestsuite("40" , "parallel-testing")
    }


    stage("PublishResults") {
        publishResults()
    }

    if ( !isSnapshot()) {
        stage('Tag') {
            tag()
        }
    }
}