def call(Map config = [:]) {
    stage('promotion') {
      task('Pomotion') {
        // echo config.dump.tostring()
        promotionConfig = [
          'buildName'            : config.bi.name,
          'buildNumber'          : config.bi.number,
          'targetRepo'           : 'docker-release-local',
          'comment'              : 'this is the promotion comment',
          'sourceRepo'           : 'docker-local',
          'status'               : 'Released',
          'includeDependencies'  : false,
          'failFast'             : false,
          'copy'                 : true
        ]
        // promote build
        config.rt.promote promotionConfig
      }
    }
  
}
