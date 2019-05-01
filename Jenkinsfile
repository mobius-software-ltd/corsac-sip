
def runTestsuite(exludedGroups = "org.restcomm.connect.commons.annotations.BrokenTests",groups = "", forkCount=1, profile="defaultProfile") {
        sh "mvn -B -f jain-sip-testsuite/pom.xml  install -DskipUTs=false  -Dmaven.test.failure.ignore=true -Dmaven.test.redirectTestOutputToFile=true -Dfailsafe.rerunFailingTestsCount=1 -DforkCount=\"$forkCount\" "
}


def buildRC() {
        // Run the maven build with in-module unit testing and sonar
        try {
        sh "mvn -B -f pom.xml -Dmaven.test.redirectTestOutputToFile=true clean install"
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
        [configFile(fileId: '37cb206e-6498-4d8a-9b3d-379cd0ccd99b',  targetLocation: 'settings.xml')]) {
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

        stage ("Build") {
            buildRC()
        }

        stage("CITestsuiteParallel") {
                runTestsuite("org.restcomm.connect.commons.annotations.UnstableTests or org.restcomm.connect.commons.annotations.BrokenTests", "org.restcomm.connect.commons.annotations.ParallelClassTests", "20" , "parallel-testing")
        }


        stage("PublishResults") {
            publishRCResults()
        }
    }
}
