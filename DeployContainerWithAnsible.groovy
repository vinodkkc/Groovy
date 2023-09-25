def call(Map config=[:]) {
    //echo config.dump().tostring()
    stage('Deploy using PODman') {
      task('Deploy using PODMAN') {
        if (!config.imageTag) {
           error("Image Tag is empty, we can't deploy the code")
        }
        def ReleaseImageName = config.dockerapp.replace('docker-local','docker-release-local')
        AddBuildSummary(ReleaseImageName)
        credntialsid = config.credentailsid
        hosts_file = 'hosts'
        obj = readYaml file: config.APPINFO_FILE
        def info = "[nodes]\n"
        obj.servers."${config.BUILD_ENV}".each { host ->
            echo ( "host ==> ${host}")
            info += "${host}\n"
        }
        if (info == "[nodes]\n") {
          error("unable to find the host name to deploy")
        }
        writeFile file: hosts_file, text: "$info"
           //docker.withRegistry(config.rts.url, 'docker-registry') {
              image = docker.image(env.ANSIBLE_DOCKER_IMAGE)
              image.pull()
        
        //   }
            image.inside {
              checkout(scm)
              withCredentails([sshUserPrivatekey(credentailsid, keyfilevariable: 'KEYFILE', passphrase: 'PASS', usernamevriable: ''USERNAME)]) {
                sshagent([credntailsid]) {
                  sh 'echo SSH_AUTH_SOCK=$SSH_AUTH_SOCK'
                  sh 'ls -al' $SSH_AUTH_SOCK || true,
                  ansibleplaybook(colorized: true,
                          disableHOSTKEYChecking: true,
                          credentialsId: credentailsid,
                          playbook: config.playbook_file,
                          inventory: hosts_file,
                          become: false,
                          extras: "-e container_image= '${ReleaseImageName}' ${config.extra_args}"
                                 )
                }
              }
            }
      }
    }
}
