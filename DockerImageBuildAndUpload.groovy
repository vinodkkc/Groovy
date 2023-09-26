def call(Map config = [:]) {
  def debug = false
  if (config.containskey('debug')) {
    debug = true
  }
  if (debug) {
    echo config.dump().toString()
  }
  if (!config?.dockerapp) {
    echo "WARNNING: docker image name cant be empty: dockerapp"
  }
  def buildkit = config.buildkit ? config.buildkit : "1"

  def DOCKER_BUILD_DIR='.'
  if(config?.DOCKER_BUILD_DIR){
    DOCKER_BUILD_DIR=config.DOCKER_BUILD_DIR
  }
  stage('Build') {
    task('Build'){
      def secrete='docker-registry'
      if(config.rts.url?.toLowerCase().contains('artifactory-dev')) {
            secrete='artifactory-dev'
      }
      if(debug) {
        echo "URL: ${config.rts.url}, secret $secret"
      }
      docker.withReistry(config.rts.url,secret) {
        def dockerfile = 'Dockerfile'
        if (config?.dockerfile){
          dockerfile = config?.dockerfile
          
        }
        def buildargs=""
        if(config?.buildargs){
          buildargs=config?.buildargs
        }
        withEnv(["DOCKER_BUILDKIT=${buildkit}"]){
          dir(DOCKER_BUILD_DIR) {
            docker.build(config.dockerapp, "--pull --no-cache ${buildargs} -f ${dockerfile}.")
          }
        }
      }
    }
    stage('upload to artifactory'){
      task('upload to Artifactory') {
        config.rtd.push(config.dockerapp, 'docker-local', config.bi)
        config.bi.env.collect()
        config.rts.publishBuildInfo config.bi
      }
    }
  }
}
