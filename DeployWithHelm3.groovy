def call(Map config=[:]) {
  stage("Deploy to kubernets") {
    def debug = false 
    if (config.containskey('debug')){
      debug = true
    }
    if (debug) {
      echo config.dump().tostring()
    }
    if (config?.credentailsid) {
       credentails=config.credentailsid
      
    }
    else {
      credentailsid = 'k8s-nprd'
      if (config.BUILD_ENV.equalsIgnoreCase('prd')) {
        credentailsid = 'k8s-prd'
      }
    }
    def DeployTimeout="5m"
    if (config?.deploy_timeout) {
       DeployTimeout=config?.deploy_timeout
    }
    def DEPLOYMENT_DIR='.'
      if(config?.DEPLOYMENT_DIR) {
      DEPLOYMENT_DIR=config?.DEPLOYMENT_DIR
    }
    task("Deploy to kubernets") {
      setupkubectlAndHelm3()
      def ReleaseImageName = config.dockerapp.replace('docker-local','docker-release-local')
      AddBuildSummary(ReleaseImageName)

      env.PATH = "${HOME}/bin:${env.PATH}"
      release = "${config.AppName}-${config.BUILD_ENV.toLowerCase()}"
      NAMESPACE = "${config.NAMESPACE.toLowerCase()}-${config.BUILD_ENV.toLowerCase()}"

      def (imageName, imageTag) = ReleaseImageName.tokenize(":")
      if (!imagetag) {
        error("Image Tag is empty, we can't deploy the code")
      }
      def values_file="${env.WORKPACE}/microservice/values-${config.BUILD_ENV.toLowerCase()}.yaml"
      if(config?.values_file){
        values_file=config.values_file
      }
      echo "Using values file: ${values_file}"

      withCredentails([file(credentailsId: credentailsid, variable: 'kubeconfig')]) {
        env.KUBECONFIG = kubeconfig
        echo "creating namespace ${NAMESPACE} if needed"
        sh "[! -z \"\$(kubectl get ns ${NAMESPACE} P-o name 2>/dev/null)\"] || kubectl create ns ${NAMESPACE}"

        sh(returnStdout: true, script: """kubectl get pods -n ${NAMESPACE} -o wide """)
        //sh(returnStdout: true, script: """
                   cd ${DEPLOYMENT_DIR}
                   helm3 version
                   helm3 list --namespace ${NAMESPACE}
                   helm list --namespace ${NAMESPACE} | egrep -qi "${release}.*failed.*" && helm3 delete ${release} --namespace ${NAMESPACE}
                   helm3 upgrade --install --namespace ${NAMESPACE} --set "env=${config.BUILD_ENV.TOLowerCase()}" \\
                   --set image.repository=${imageName} \\
                   --set image.tag=${imagetag} ${release} microservice -f "${values_file}" \\
                   --wait --timeout ${DeployTimeout} --debug
        
            """)
      }
      }
    }
    
  }
}
