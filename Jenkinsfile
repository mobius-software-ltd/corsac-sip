
def runTestsuite(forkCount=1, profile="defaultProfile") {
        sh "mvn -B -f jain-sip-testsuite/pom.xml  install -DskipUTs=false  -Dmaven.test.failure.ignore=true -Dmaven.test.redirectTestOutputToFile=true -Dfailsafe.rerunFailingTestsCount=1 -DforkCount=\"$forkCount\" "
}


def buildRC() {
        // Run the maven build with in-module unit testing and sonar
        try {
        sh "mvn -B -f pom.xml -Dmaven.test.redirectTestOutputToFile=true clean deploy"
        } catch(err) {
            publishRCResults()
            throw err
        }
}

def publishRCResults() {
    junit testResults: '**/target/surefire-reports/*.xml', testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    checkstyle canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '**/checkstyle-result.xml', unHealthy: ''
    step( [ $class: 'JacocoPublisher' ] )
}

node("cxs-testsuite-large_docker") {

    configFileProvider(
        [configFile(fileId: 'c33123c7-0e84-4be5-a719-fc9417c13fa3',  targetLocation: 'settings.xml')]) {
	    sh 'mkdir -p ~/.m2 && sed -i "s|@LOCAL_REPO_PATH@|$WORKSPACE/M2_REPO|g" $WORKSPACE/settings.xml && cp $WORKSPACE/settings.xml -f ~/.m2/settings.xml'
    }

    stage ('Checkout') {
        checkout scm
    }

    // Define Java and Maven versions (named according to Jenkins installed tools)
    // Source: https://jenkins.io/blog/2017/02/07/declarative-maven-project/
    String jdktool = tool name: "JenkinsJava7"
    def mvnHome = tool name: 'Maven-3.5.0'

    // Set JAVA_HOME, and special PATH variables.
    List javaEnv = [
            "PATH+MVN=${jdktool}/bin:${mvnHome}/bin",
            "M2_HOME=${mvnHome}",
            "JAVA_HOME=${jdktool}"
    ]

    withEnv(javaEnv) {

        stage('Versioning') {
            sh 'mvn -B versions:set -DnewVersion=${MAJOR_VERSION_NUMBER} versions:commit'
        }

        stage ("Build") {
            buildRC()
        }

        stage("CITestsuiteParallel") {
                runTestsuite("20" , "parallel-testing")
        }


        stage("PublishResults") {
            publishRCResults()
        }

        stage('Tag') {
            // Save release version
            def pom = readMavenPom file: 'pom.xml'
            releaseVersion = pom.version
            echo "Set release version to ${releaseVersion}"

            withCredentials([usernamePassword(credentialsId: 'c2cce724-a831-4ec8-82b1-73d28d1c367a', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                sh("git commit -a -m \"New release candidate ${releaseVersion}\"")
                sh("git tag ${releaseVersion}")
                sh('git push https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/telestax/telscale-jsip.git --tags')
            }
        }
    }
}
