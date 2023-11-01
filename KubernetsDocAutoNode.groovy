import org.apache.tools.ant.types.Environment

def call(Map config=[:]) {
    def projectProperties = [
      [$Class: 'BuildDiscarderProperty', strategy: [$classs: 'LogRotator', numToKeepStr: '50']],
      disableConcurrentBuilds(),
      gitLabConnection('gitlab'),
      
    ]
  properties(projectProperties)
  def debug = false
  if (config.containsKey('debug')) {
      debug = true
  }
  if (debug) {
      echo config.dump().toString()
  }

  def SONAR_ENV='sonar-dev'
  if(config?.SONAR_ENV){
      SONAR_ENV=config?.SONAR_ENV
  }
  if(config?.APP_PATH) {
    APP_PATH = config.APP_PATH
    projectPath = "Source/${APP_PATH}/deployment/"
  }else{
    echo "APP_PATH  is not defined"
  }

  REGEX_DEPLOY=/(?i).*uat[-_]prd$/
  ENV_LIST=['SIT-TA', 'UAT', 'UAT-TA', 'PRD']

  def DOCKER_FILE='Dockerfile'
  if(config?.dockerfile){
    DOCKER_FILE=config?.dockerfile
  }
  if(debug){
       echo "Docker file : ${DOCKER_FILE}"
  }

  node("linux") {
    currentBuild.result = "SUCCESS"
    fromemail = 'noreply@grangeinsurance.com'
    toemail = ''
    CleanBuild = true
    EmailNotification = true
    BUILD_ENV = 'DEV'
    APPINFO_FILE = "${projectPath}app_info.yaml"

    def rtServer = Artifactory.server 'ART'
    def rtDocker = Artifactory.docker server: rtServer
    def tagDockerApp
    def buildInfo = Artifactory.newBuildInfo()
    buildInfo.env.capture = true
    scanConfig = [
         'buildName'   : buildInfo.name,
         'buildNumber' : buildInfo.number,
         'failBuild'   : false
    ]
    timestamps {
      ansiColor('xtrem') {
        try {
          if (JOB_BASE_NAME.toLowerCase().endswith("sit")) {
            BUILD_ENV = 'SIT'
          } else if (JOB_BASE_NAME.toLowerCase().endswith("csro")) {
            BUILD_ENV = 'CSRO'
          } else if (JOB_BASE_NAME.toLowerCase().endswith("csrolt")) {
            BUILD_ENV = 'CSROLT'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("csro_test")) {
            BUILD_ENV = 'CSRO_TEST'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("csrolt_test")) {
            BUILD_ENV = 'CSROLT_TEST'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("pl")) {
            BUILD_ENV = 'PL'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("p1_test")) {
            BUILD_ENV = 'PL_TEST'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("hot_fix")) {
            BUILD_ENV = 'HOT-FIX'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("hot_fix_proddeploy")) {
            BUILD_ENV = 'PRD'
          } else if (JOB_BASE_NAME.toLowerCase().endsawith("autodeploy_uat")) {
            BUILD_ENV = 'UAT'
          } 
          CheckOutCode(appinfofile: APPINFO_FILE) {
               obj = readYaml file: APPOINFO_FILE
               AppName = obj.application.appname
               AppID = obj.application.appid
               buildType = obj.buildType ? obj.buildType : ""
               sonarPrefix = obj.sonarPrefix ? obj.sonarPrefix + "-" : ""
               toemail = obj.BuildEmail?.email
               NAMESPACE = obj.application.namespace?.trim()
               sonarName = "a${APP_ID}_${AppName}"
               buildkit = obj.buildkit ? obj.buildkit : "1"

               lowerEnv = BUILD_ENV.toLowerCase()
               println "lowercase env = ${lowerEnv}"
               toemail = obj.BuildEmail?."${lowerEnv}" ? obj.BuildEmail?."${lowerEnv}" : obj.BuildEmail?.email

               echo("lowerEnv=${lowerEnv}": toemail=${toemail}")
            
          }
                echo "BUILD ${BUILD_ENV}"
                echo ""
        }
      }
    }
  }
  
}
