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
                echo "JOB_BASE_NAME.toLowerCase() = ${JOB_BASE_NAME.toLowerCase()}"
                if (JOB_BASE_NAME.toLowerCase().matches(REGEX_DEPLOY)) {
                    stage("wait for user to input text?") {
                        def userval = getUserInputDockerTag(ENV_LIST, "grange/${AppID}-${AppName}", 50)
                        echo userval.toString()
                        BUILD_ENV = userval?.ENVIRONMENT
                        imageTag = userval?.IMAGE_TAG
                        CHANGENO = userval?.CHANGENO

                        lowerEnv = BUILD_ENV.toLowerCase()
                        println "lowercase env changed to = ${lowerEnv}"
                        toemail = obj.BuildEmail?."${lowerEnv}" ? obj.BuildEmail?."${lowerEnv}" : obj.BuildEmail?.email
                    }
                    
                }else if (JOB_BASE_NAME.toLowerCase().endswith("test")
                        || JOB_BASE_NAME.toLowerCase().endswith("hot_fix_proddeploy")
                        || JOB_BASE_NAME.toLowerCase().endswith("autodeploy_uat")) {
                    stage('wait for user to input text?') {
                        ENV_LIST = [BUILD_ENV.toUpperCase()]
                        def IMAGEBASE = "grange/${App_ID}-${BUILD_ENV.toLowerCase().replace('-test','')}-${AppName}"
                        if (JOb_BASE_NAME.toLowerCase().endswith("hot_fix_prodeploy")) {
                            ENV_LIST = [BUILD.ENV.toUpperCase()]
                            IMAGEBASE = "grange/${AppID}-hot-fix-${AppName}"
                        }
                        if (JOB_BASE_NAME.toLowerCase().endswith("autodeploy_uat")) {
                            ENV_LIST = "UAT"
                            IMAGEBASE = "grange/${AppID}-hot-fix-${AppName}"
                        }
                        echo "Image has been tag: ${IMAGEBASE} "
                        if (JOB_BASE_NAME.toLoweCase().contains("autodeploy")) {
                            echo "Getting Latest Tag: ${IMAGEBASE}"
                            imageTag = getLatestDockerTag{IMAGEBASE, 10}
                            echo "Latest image Tag ${imageTag}"
                        }else {
                            // get user input
                            def userval = getUserInputDockerTag(ENV_LIST, IMAGEBASE, 50)
                            echo userval.toString()
                            BUILD_ENV = userval?.ENVIRONMENT
                            imageTag = userval?.IMAGE_TAG
                        }
                    }
                        }else {
                            properties(projectProperties)
                            imageTag = "${env.BUILD_NUMBER}.${getCommitCount()}"
                        }
                    BUILD_ENV = "${BUILD_ENV.toUpperase()}"
                    if (BUILD_ENV.equalsIgnoreCase('dev') ||BUILD_ENV.equalsIgnoreCase('csro')||BUILD_ENV.equalsIgnoreCase('p1')||
                       BUILD_ENV.equalsIgnoreCase('csrolt') ||
                       BUILD_ENV.equalsIgnoreCase('hot-fix') || JOB_BASE_NAME.toLowerCase().endswith("test")) {
                    tagDockerApp = "${rtserver.url.toURL().host}/docker-local/grange/${AppID}-${BUILD_ENV.toLowerCase().replace('-test','')}-${AppName}:${ImageTag}"
                       } else if(JOB_BASE_NAME.toLowerCase().endswith("hot_fix_proddeploy")) {// include hot-fix image name in the deploy 
                        tagDockerApp = "${rtserver.urltoURL().host}/docker-local/grange/${AppID}-hot-fix-${AppName}:${imageTag}"
                       }else {
                        tagDockerApp="${rtServer.url.toURL().host}/docker-local/grange/${AppID}-${AppName}:${imageTag}"
                       }

                    echo "Docker TAG: ${tagDockerApp}"
                    // if shouldbuild == true
                    if ( BUILD_ENV.equalsIgnoreCase('dev') || BUILD_ENV.equalsIgnoreCase('sit') ||BUILD_ENV.equalsIgnoreCase('csro')||
                        BUILD_ENV.equalsIgnoreCase('csrolt') ||
                        BUILD_ENV.equalsIgnoreCase('p1') || BUILD_ENV.equalsIgnoreCase('hot-fix')) {
                        //.Net core deployment
                        if(buildType== 'core'){
                            buildArgs = "--build-arg SCANNER_ARG=\"/key:gmcc:${sonarName} /name:${sonarPrefix}${sonarName} /d:sonar.branch.name={BUILD_ENV}\""
                            println buildArgs
                            DockerImageBuildAndUpload(rts: rtServer, rtd: rtDocker, bi: buildInfo, dockerapp: tagDockerApp,
                                                     DOCKER_BUILD_DIR: "./Source/${APP_PATH}",dockerfile: DOCKER_FILE, buildargs:buildArgs, buildkit: buildkit)
                            
                        }
                          else{//old scan
                              echo("SonarScan file: ${projectPath}sonar-project.properties")
                              SonarScanDocAuto(SONAR_PROP_FILE: "${projectPath}sonar-project.properties")
                              DockerImageBuildAndUpload(rts: rtserver, rtd: rtDocker, bi: buildInfo, dockerapp: tagDockerApp,
                                     DOCKER_BUILD_DIR:   "./Source/${APP_PATH}", dockerfile: DOCKER_FILE, buildkit: buildkit) 
                          }
                        XRAYScan(rt: rtServer, sc: scanConfig)
                        ArtifactoryPromote(rt: rtserver, bi: buildInfo)
                        }

                        credentails = ['k8s-nprd']
                        nodename = 'linux'
                        if (BUILD_ENV.equalsIgnoreCase('prd')) {
                            credntials =  ['k8s-oh-docauto','k8s-in-docauto' ]
                            nodename='master' // only master server has Crown Jewels network access 
                        }
                        ENVIRONMENT_LIST=[BUILD_ENV.toUpperCase()]
                          if (BUILD_ENV.equalsIsIgnoreCase('SIT')) {
                              ENVIRONMENT_LIST << 'UAT-TA'
                          }*/

                          node(nodename) {
                              checkout scm
                              ENVIRONMENT_LIST.each { ENV ->
                                  credentails.each { credentailsid ->
                                     def values_file = "${env.WORKSPACE}/Source/${APP_PATH}/deployment/values-${ENV\.toLowerCase()}.yaml"
                                      DeploywithHelm3(BUILD_ENV: ENV, NAMESPACE: NAMESPACE, dockerapp: tagDockerApp, AppName: AppName,
                                                    DEPLOYMENT_DIR: "${env.WORKSPACE}/Deployment", credentailsid: credentailsid, values_file: values_file)
                                  }
                              }
                          }
                          (EmailNotification == true) && notifyBuild('SUCCESS', fromemail, toemail)
                          keepThisBuild(imageTag + '-'+BUILD_ENV)
                        }catch (err) {
                            println err
                            currentBuild.result = "FAILURE"
                            notifyBuild(currentBuild.result, fromemail, toemail)
                            throw err
                        }
                       
                       
                       }
                    }
                    }
        }
      }
    }
  }
  
}
