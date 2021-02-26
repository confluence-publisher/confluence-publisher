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
        DOCKER_REG_CREDS_ID = 'docker.io'
        TRY_COUNT = 5
        AWS_REGISTRY = '736549184051.dkr.ecr.eu-central-1.amazonaws.com'
        DOCKER_COORDS = "${env.AWS_REGISTRY}/confluence-publisher"
	
    }
    parameters {
        booleanParam(name: 'DO_CLEAN', defaultValue: true, description: 'Whether or not to clean the workspace.')
        booleanParam(name: 'DO_BUILD', defaultValue: true, description: 'Whether or not to perform a build.')
        booleanParam(name: 'DO_PUBLISH', defaultValue: true, description: 'Whether or not to publish the artifact.')
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
                sh "./mvnw -nsu versions:set -DnewVersion=${env.BUILD_VERSION}"
                sh "./mvnw -nsu -Ddocker.repo=${env.DOCKER_COORDS} clean install"
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
                    sh "aws ecr get-login-password --region ${env.AWS_PROD_ECR_PRIVATE_REGION} | docker login --username AWS --password-stdin $AWS_REGISTRY"
                    sh "docker tag ${env.DOCKER_COORDS}:${env.BUILD_VERSION} ${env.DOCKER_COORDS}:latest"
                    sh "docker push ${env.DOCKER_COORDS}:latest"
                    sh "docker push ${env.DOCKER_COORDS}:${env.BUILD_VERSION}"
            }
        }
    }
}
