def call(String ReleaseImageName) {
  manager.createSummary("/plugin/workflow-job/images/48x48/pipelinejob.png").appendText("container Image:<b> ${ReleaseImageName} </b>", false, false, false,"green")
}
