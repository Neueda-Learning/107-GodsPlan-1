// Jenkins declarative pipeline for God's Plan (backend + frontend + nginx via Docker Compose).
//
// Prerequisites on the Jenkins agent:
//   - Docker Engine + Docker Compose v2 plugin ("docker compose ...")
//   - Docker Pipeline plugin (for docker.image(...).inside(...))
//   - JUnit plugin (test reporting) — optional but recommended
//
// Required Jenkins credentials:
//   - Secret file credential "godsplan-env-file" containing the deployment .env
//     (same keys as .env.example: MYSQL_*, EXCHANGE_RATE_API_KEY, VITE_API_URL,
//     NGINX_PORT, APP_ENVIRONMENT, ANALYTICS_*).
//
// This pipeline builds and tests the backend (Maven) and frontend (npm) in
// throwaway containers, then builds and deploys the full stack on the agent
// host with Docker Compose (mysql, api, web, nginx), matching the workflow
// documented in README.md.

pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    timeout(time: 45, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  parameters {
    choice(name: 'DEPLOY_ENV', choices: ['staging', 'production'], description: 'Target deployment environment')
    booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip backend/frontend test stages')
    booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Build and deploy the stack with Docker Compose after tests pass')
  }

  environment {
    COMPOSE_PROJECT_NAME = "godsplan-${params.DEPLOY_ENV}"
    ENV_FILE_CREDENTIAL_ID = 'godsplan-env-file'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Backend: Build & Test') {
      when { expression { !params.SKIP_TESTS } }
      steps {
        dir('backend') {
          script {
            docker.image('maven:3.9.9-eclipse-temurin-21').inside {
              sh 'mvn -B clean verify'
            }
          }
        }
      }
      post {
        always {
          junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: true
        }
      }
    }

    stage('Frontend: Install & Lint') {
      steps {
        dir('frontend') {
          script {
            docker.image('node:22-alpine').inside {
              sh 'npm ci'
              sh 'npm run lint'
            }
          }
        }
      }
    }

    stage('Frontend: Test') {
      when { expression { !params.SKIP_TESTS } }
      steps {
        dir('frontend') {
          script {
            docker.image('node:22-alpine').inside {
              sh 'npm run test -- --run'
            }
          }
        }
      }
    }

    stage('Frontend: Build') {
      steps {
        dir('frontend') {
          script {
            docker.image('node:22-alpine').inside {
              sh 'npm run build'
            }
          }
        }
      }
    }

    stage('Prepare .env') {
      when { expression { params.DEPLOY } }
      steps {
        withCredentials([file(credentialsId: env.ENV_FILE_CREDENTIAL_ID, variable: 'ENV_FILE')]) {
          sh 'cp "$ENV_FILE" .env'
        }
      }
    }

    stage('Build & Deploy') {
      when { expression { params.DEPLOY } }
      steps {
        sh '''
          docker compose build
          docker compose up -d --remove-orphans
          docker image prune -f
        '''
      }
    }

    stage('Health Check') {
      when { expression { params.DEPLOY } }
      steps {
        sh '''
          . ./.env
          PORT="${NGINX_PORT:-8081}"
          for i in $(seq 1 30); do
            if curl -fsS "http://localhost:${PORT}/actuator/health" | grep -q '"status":"UP"'; then
              echo "Backend is healthy"
              exit 0
            fi
            echo "Waiting for backend health... ($i/30)"
            sleep 5
          done
          echo "Backend failed health check" >&2
          docker compose logs --tail=200
          exit 1
        '''
      }
    }
  }

  post {
    failure {
      sh 'docker compose logs --tail=200 || true'
    }
    always {
      sh 'rm -f .env || true'
    }
  }
}
