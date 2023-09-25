def call(Map config=[:], Closure body) {
    stage('pre Build setup') {
      task('Clean workdspace') {
        deleteDir()
      }
      task('Check Out') {
        checkout scm

        if(config?.appinfofile) { // check appinfo file
            files = findFiles(glob: config.appinfofile)
            filename = files[0]
            echo("filename :$filename")
            if (!fileExists(filename?.tostring())) {
                error("app_info.yaml file is missing")
            }
        }
        body()
      }
    }
}
