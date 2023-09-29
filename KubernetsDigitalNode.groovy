def call() {
  def projectProperties = [
    [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '50']],
    disableConcurrentBuilds(),
    gitLabConnection('gitlab'),
    pipelineTriggers([
     gitlab(triggerOnPush: true, triggerOnmergeRequest: false, triggerOnAcceptedMergeRequest: true, branchFilterType: 'All') 
    ]),
  ]

  final SONAR_ENV='sonar-prd'
  final REGEX_DEPLOY=/(?i).*[-_]prd$/
  final REGEX_DEPLOY_TEST=/(?i).*[-_]devops$/

  node("linux") {
    currentBuild.result = "SUCCESS"
    foremail = 'noreply@grangeinsurance.com'
    toemail = ''
    EmailNotification = false

    branch = getCleanString( env.BRANCH_NAME)
    if (branch !=null){branch = branch.toLowerCase()}
    sonarBranch = branch
    println "branch = ${branch}"
    if  (branch == "develop"){ deployEnv = 'DEV'}
    else if (branch == "qa"){ deployEnv = 'QA'}
    else if (branch !=null && (branch.contains("gpcd") || branch == "devops")){ deployEnv = "Dev"
     //email.emailNotifications=false
      EmailNotification = false
      println "devops test"  }                                                   
    else if (branch = "main") {
      deployEnv = "UAT"
      sonarBranch = "master"}
    else {
      deployEnv = "none"
      println "default branch"
    }
    final APPINFO_FILE="app_info.yaml"
    shouldBuild = true

    def rtserver = Artifactory.server 'ART'
    //if(config?.REPO_ENV && config?.REPO_ENV.equalaIgnoreCase('Dev')){
    rtserver = Artifactory.server 'bARI-DEV'
    //}
    def rtDocker = Artifactory.docker server: rtserver
    def tagDockerApp
    def buildInfo = Artifactory.newBuildInfo()
    buildInfo.env.capture = true
    scanConfig = [
      'buildName' : buildInfo.name,
      'buildNumber' : buildinfo.number,
      'failBuild' : false
    ]
    timestamps {
      ansiColor('xterm') {
        try {
          checkOutcode(appinfofile: APPINFO_FILE) {
            obj = readYaml file: APPINFO_FILE
            AppName = obj.application.appname
            AppName = AppName.trim().replaceAll('_','_')
            AppID = obj.application.appid
            sonarprefix = obj.sonarprefix + "_" ? obj.sonarprefix : ""
            toemail = obj.buildEmail?.email
            buildType = obj.buildType ? obj.buildType : ""
            namespace = obj.application.namespace?.trim()?.replaceAll('_','-')
            envList = getEnvs(obj.servers)
            dockerfile = obj.dockerfile
            project = obj.project
            keytab = obj.keytab ? obj.keytab : ""
            healthCheck = obj.validation?.URL ? obj.validation?.URL : ""
          }
          def dockerfileText = libraryResource(dockerfile)
          dockerfileText = dockerfileText.replace('$PROJECT',project)
          scanner = "/key:gmcc:${AppName} /name.${sonarPrefix}_${AppName} /d:sonar.branch.name=${sonarBranch}"
          dockerfileText = dockerfileText.replace('$SCANNER_ARG',scanner)
          //localFilec.write(localText)
          writeFile file: "DigitalDockerfile", text:dockerfileText
          final DOCKER_FILE = "DigitalDockerfile"
          echo "Docker file" : ${DOCKER_FILE}"

          if(keytab != "")
          {
            def valuesText = libraryResource("digital-k8s-values.yaml")
            valuesText = valuesText.replace('$ENV', deployEnv.toLowerCase())
            valuesText = valueText.replace('$KEYTAB', keytab)
            valuesText = valurText.replace('$HEALTHCHECK', AppName)
            def host = deployEnv == "PRD" ? "k8s-prd.grangeinsurance.com" : "k8s-nprd.grangeinsurance.com"
            valuesText = valuesText.replace('$HOST', host)
            //LocalFile.write(localText)
            def valuesFileName = "microservice/values-${deployEnv.toLowerCase()}.yaml"
            writeFile file: valuesFileName, text: valuesText
          }
          if (JOB_BASE_NAME.toLowerCase().matches(REGEX_DEPLOY)) {
            stage('wait for user to input text?'){
              //todo: get branch with tag
              //branch = "qa"
              //getSingleSelect(["hotfix-deployment","qa"], please select a source branch", "Branch", "Branch", 60)
              branch=getSingleSelect(getBranches(),  please select a source branch", "Branch", "Branch", 60)
              //def userval = getUserInputDockerTag(envlist, "grange/${{APPID}-${branch}-${AppName}", 50)
              def userval = getUserInputDockerTag(envlist, "grange/${{APPID}-${branch}-${AppName}", 50)
              echo userval.toString()
              deployEnv = userval?.ENVIRONMENT
              imageTag = userval?.Image_TAG
              CHANGEDNO = userval?.CHANGENO
              shouldBuild = false

            }
            else{
              properties(projectProperties)
              imageTag = "${env.BUILD_NUMBER}.${getCommitCount()}"
            }
           deployEnv= "${deployEnv.toUpperCase()}"
           //todo: change env to branch ${deployEnv.toLowerCase()}
           tagDockerApp = "${rtserver.url.toURL().host}/docker-local/grange/${AppID}-${branch}-${AppName}:${imageTag}"

           echo "Docker App: ${tagDockerApp}, Name Space: ${namespace}, deployEnv: ${deployEnv}"
           if ( shouldBuild){
             if(buildType=="core")
                 buildArgs= "--build-arg SCANNER_ARG=\"/key:gmcc:${AppName} /name:${sonarPrefix}${AppName} /d:sonar.branch.name"
                 println buildArgs
                 DockerImageBuildAndUpload(rts: rtserver, rtd: rtDocker, bi: buildInfo, dockerapp: tagDockerApp,dockerfile: DOCKERFILE) 
           }else{
             setSonarProperties(AppName, sonarprefix)
             SonarScan(SONAR_ENV)
             DockerImageBuildAndUpload(rmts: rtserver, rtd: rtDocker, bi: buildInfo, dockerapp: tagDockerApp,dockerfile: DOCKERFILE)
           }
           XRAYScan(rt: rtserver, sc: scanConfig)
           ArtifactoryPromote(rt: rtserver, bi: buildinfo)
          }
           if (!deployEnv.equalsIgnoreCase("none")){
             DeployWithHelm3(BUILD_ENV: deployEnv, NAMESPACE: namespace, dockerapp: tagDockerApp, AppName: AppName)
           }
            (EmailNotification == true) && notifyBuild('SUCCESS', fromemail, toemail)
             keepThisBuild(imageTag)
          
        } catch (err) {
          echo err?.getMessage()
          currentBuild.result = "FAILURE"
          (EmailNotification == true) && notifyBuild(currentBuild.result, fromemail, toemail)
          throw err
        }
                                                  
      }
    }
    }
  }
    def getEnvs(LinkedHashMap envMap)
    {
      def envList = []
      if (envMap){
        for (Map.Entry<string, ArrayList<string>> entry : envMap.entryset()) {
          string key = entry.getkey();
          //List<String> value = entry.getKey();
          //println key
          if (key.toLowerCase() != "dev")
             envList.add(key)
        }
      }
      return envList
    }
}
