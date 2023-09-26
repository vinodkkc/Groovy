def call(String appName, string sonarEnv, string branch, string deployEnv, string packageName, LinkedHasMap buildParams, boolean forceMaster = false)
{
  def shouldTest = buildParams.unitTests
  def sonarName = buildParams.sonarPrefix ? "${buildParams.sonarPrefix}_${appName}" : appName
  stage('Build'){
     def branchArg = "/d:sonar.branch.name=${branch}"
     if (branch == null || forceMaster){
       branchArg = ""
     }
    task('Start Sonarqube'){
      withEnv(["JAVA_HOME=D:\\Java\\jdk-11"]) {
        echo env.JAVA_HOME
        def scannerHome = tool 'SonarScannerMSBuild'
        withSonarQubeEnv(sonarEnv) {
          bat "${scannerHome}\\SonarScanner.MSBuild.exe begin /key:${appName} /name;${sonarName} /version:1.0 ${branchArg} /d:sonar.vb.file.suffixes=.bas /d:sonar .cs.opencover.reportsPaths=\"*Coverage.xml\""
        }
      }
    }
    task('Build'){

      powershell "New-Item -Item-ype Directory -Force -path d:\\$appName\\$deployEnv\\publish\\"

      bat "D:\\software\\nuget\\nuget.exe restore \"${buildParams.solution}\""

      def stdout = bat "\"${buildParams.msBuild}\" \"${buildParams.solution}\" /p:DeployOnBuild=true /p:Configuration=Release

      Println stdout
    }
    if(shouldTest){
      tast('Test'){
         def testscript = libraryResource("unitTests.ps1")
         //def valuesFileName = "microservice/values-${deployEnv.toLowerCase()}.yaml"
        writeFile file: "unitTests.ps1", text: testScript
        def SourceDir = pwd()
        def testout = powershell label: '', script: "${sourceDir}/unittests.ps1 ${sourceDir}"
        println testout
      }
    }
    task('End sonarqube') {
      withEnv(["JAVA_HOME=D:\\Java\\jdk-11"]) {
        echo env.JAVA_HOME
        def out = bat "if exist ${env.JAVA_HOME}\\bin\\java.exe echo thing"
        def scannerHome = tool 'SonarScannerMSBuild'
        withSonarQubeEnv(sonarEnv) {
          bat "${scannerHome}\\SonarScanner.MSBuild.exe end"
        }
      }
    }
    task('zip') {
      makeDir("d:\\jenkins\\zips")
      def zipout = powershell "Compress-Archieve -LiteralPath \"d:\\$appName\\$deployEnv\\publish\" -DestinationPath \" d: \\jenkins\\zips\\$packageName\""
      println zipout
    }
  }
}
