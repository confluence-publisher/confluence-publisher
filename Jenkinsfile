pipeline {
    triggers {
        pollSCM '* * * * *'
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '40'))
        skipDefaultCheckout()
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        ansiColor('xterm')
    }
    agent {
        label 'docker'
    }
    environment {
        ARTIFACT_REPO_CREDS_ID = 'mindcurv-jenkins-artifactory-api-key'
        DOCKER_REG_CREDS_ID = 'docker.io'
        MAVEN_REPO_LOCAL = '.m2'
        M2_SETTINGS_CREDS_ID = 'maven-settings-for-mindcurv-artifactory'
        TRY_COUNT = 5
    }
    parameters {
        booleanParam(name: 'DO_CLEAN', defaultValue: true, description: 'Whether or not to clean the workspace.')
        booleanParam(name: 'DO_BUILD', defaultValue: false, description: 'Whether or not to perform a build.')
        booleanParam(name: 'DO_PUBLISH', defaultValue: false, description: 'Whether or not to publish the artifact.')
        booleanParam(name: 'DEBUG', defaultValue: false, description: 'Whether or not to enable DEBUG mode.')
        booleanParam(name: 'TRACE', defaultValue: false, description: 'Whether or not to enable TRACE mode.')
    }
    stages {
        stage('Pre') {
            parallel {
                stage('Version: bash') { steps { sh 'bash --version' } }
                stage('Version: docker') { steps { sh 'docker -v' } }
            }
        }
        stage('Clean') {
            when { expression { env.DO_CLEAN != 'false' } }
            steps {
                deleteDir()
            }
        }
        stage('Checkout') {
            steps {
                retry(env.TRY_COUNT) {
                    timeout(time: 45, unit: 'SECONDS') {
                        checkout scm
                    }
                }
            }
        }
        stage('Version') {
            steps {
                script {
                    env.BUILD_VERSION = sh(script: 'cat .version', returnStdout: true).trim() + '.' + env.BUILD_ID
                    currentBuild.displayName = "${env.BUILD_VERSION}"
                }
                echo "Version: ${env.BUILD_VERSION}"
            }
        }
        stage('Build & Unit Tests') {
            when { expression { params.DO_BUILD } }
            steps {
                withMaven(maven: 'apache-maven-3.6.2') {
                    sh "mvn clean install"
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: '**/target/**/TEST-*.xml', fingerprint: true, allowEmptyArchive: true
                    junit allowEmptyResults: true, testResults: '**/target/**/TEST-*.xml'
                }
            }
        }
        stage('Publish') {
            when { expression { params.DO_PUBLISH } }
            steps {
                withCredentials([
                        usernameColonPassword(credentialsId: env.ARTIFACT_REPO_CREDS_ID, variable: 'ARTIFACT_REPO_AUTH'),
                        usernamePassword(credentialsId: env.DOCKER_REG_CREDS_ID, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')
                ]) {
                    sh 'make publish'
                }
            }
        }
    }
}
